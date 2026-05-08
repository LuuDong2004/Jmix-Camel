package com.vn.jmixcamel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses Extract node code into a list of {@link ParsedLine}s. Each line is
 * {@code target = expression}.
 *
 * <p>LHS forms:
 * <ul>
 *   <li>bare alias ({@code email}) — writes to {@code <defaultTarget>.<alias>}</li>
 *   <li>{@code @output.X} or {@code @private.X} — absolute path</li>
 * </ul>
 *
 * <p>RHS expression: operands joined by {@code +}. Operand types:
 * <ul>
 *   <li>identifier path: {@code output.user.email}</li>
 *   <li>string literal: {@code "..."} or {@code '...'} (no escape support)</li>
 *   <li>number literal: {@code 42}, {@code 3.14}, {@code -1}</li>
 *   <li>keyword: {@code true}, {@code false}, {@code null}</li>
 * </ul>
 *
 * <p>Comments: lines starting with {@code #} or {@code //}.
 */
public final class ExtractCodeParser {

    private static final Pattern IDENT_RE = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    /** Path: first segment must start with letter/_; later segments may start with digit (array index). */
    private static final Pattern PATH_RE = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)*");

    private ExtractCodeParser() {}

    public enum PartKind { STRING, NUMBER, BOOL, NULL, REF }

    public static final class ExprPart {
        public final PartKind kind;
        /** For STRING/REF: String. For NUMBER: Double or Long. For BOOL: Boolean. NULL ignored. */
        public final Object value;
        public ExprPart(PartKind kind, Object value) {
            this.kind = kind;
            this.value = value;
        }
    }

    public static final class ParsedLine {
        public final int lineNum;
        public final boolean isComment;
        public final boolean isAbsolute;
        public final String targetPath;          // e.g. "output.user.email"
        public final List<ExprPart> parts;
        public final String error;

        public ParsedLine(int lineNum, boolean isComment, boolean isAbsolute,
                          String targetPath, List<ExprPart> parts, String error) {
            this.lineNum = lineNum;
            this.isComment = isComment;
            this.isAbsolute = isAbsolute;
            this.targetPath = targetPath;
            this.parts = parts;
            this.error = error;
        }
    }

    /**
     * @param code           multi-line code
     * @param defaultTarget  e.g. "output.user" or "private.cache"; required for bare-alias lines
     */
    public static List<ParsedLine> parse(String code, String defaultTarget) {
        List<ParsedLine> out = new ArrayList<>();
        if (code == null) return out;
        String[] lines = code.split("\\R", -1);
        List<ExprPart> empty = new ArrayList<>();
        for (int idx = 0; idx < lines.length; idx++) {
            int lineNum = idx + 1;
            String raw = lines[idx];
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("#") || trimmed.startsWith("//")) {
                out.add(new ParsedLine(lineNum, true, false, "", empty, null));
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                out.add(new ParsedLine(lineNum, false, false, "", empty,
                        "Dòng " + lineNum + ": thiếu '=' — cú pháp: target = expression"));
                continue;
            }
            String lhs = trimmed.substring(0, eq).trim();
            String rhs = trimmed.substring(eq + 1).trim();

            boolean isAbsolute = false;
            String targetPath;

            try {
                if (lhs.startsWith("@")) {
                    isAbsolute = true;
                    String stripped = lhs.substring(1);
                    int dot = stripped.indexOf('.');
                    if (dot < 0) throw new ParseError("override phải dạng @output.X hoặc @private.X");
                    String scope = stripped.substring(0, dot);
                    String rest = stripped.substring(dot + 1);
                    if (!"output".equals(scope) && !"private".equals(scope)) {
                        throw new ParseError("scope phải là output hoặc private (đang là '" + scope + "')");
                    }
                    if (!PATH_RE.matcher(rest).matches()) {
                        throw new ParseError("path '" + rest + "' không hợp lệ");
                    }
                    targetPath = scope + "." + rest;
                } else {
                    if (!IDENT_RE.matcher(lhs).matches()) {
                        throw new ParseError("alias '" + lhs + "' không hợp lệ — chỉ chữ/số/_, không có dấu chấm");
                    }
                    if (defaultTarget == null || defaultTarget.isBlank()) {
                        throw new ParseError("chưa có default target — cần chọn ở 'Output Mapping' hoặc dùng @output.X / @private.X tuyệt đối");
                    }
                    targetPath = defaultTarget + "." + lhs;
                }
                List<ExprPart> parts = parseExpr(tokenize(rhs));
                out.add(new ParsedLine(lineNum, false, isAbsolute, targetPath, parts, null));
            } catch (ParseError e) {
                out.add(new ParsedLine(lineNum, false, isAbsolute, "", empty,
                        "Dòng " + lineNum + ": " + e.getMessage()));
            }
        }
        return out;
    }

    // -------------------- internal --------------------

    private static final class ParseError extends RuntimeException {
        ParseError(String msg) { super(msg); }
    }

    private enum TokKind { STRING, NUMBER, IDENT, PLUS }

    private static final class Tok {
        final TokKind kind;
        final String value;
        Tok(TokKind kind, String value) { this.kind = kind; this.value = value; }
    }

    private static List<Tok> tokenize(String s) {
        List<Tok> tokens = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t') { i++; continue; }
            if (c == '"' || c == '\'') {
                char quote = c;
                int start = i + 1;
                i++;
                StringBuilder v = new StringBuilder();
                while (i < s.length() && s.charAt(i) != quote) { v.append(s.charAt(i)); i++; }
                if (i >= s.length()) throw new ParseError("chuỗi không đóng ngoặc bắt đầu cột " + start);
                i++; // consume closing quote
                tokens.add(new Tok(TokKind.STRING, v.toString()));
                continue;
            }
            if (Character.isDigit(c) || (c == '-' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
                StringBuilder v = new StringBuilder();
                if (c == '-') { v.append('-'); i++; }
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                    v.append(s.charAt(i)); i++;
                }
                tokens.add(new Tok(TokKind.NUMBER, v.toString()));
                continue;
            }
            if (c == '+') { tokens.add(new Tok(TokKind.PLUS, "+")); i++; continue; }
            if (Character.isLetter(c) || c == '_') {
                StringBuilder v = new StringBuilder();
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) {
                    v.append(s.charAt(i)); i++;
                }
                tokens.add(new Tok(TokKind.IDENT, v.toString()));
                continue;
            }
            throw new ParseError("ký tự lạ '" + c + "' tại cột " + (i + 1));
        }
        return tokens;
    }

    private static List<ExprPart> parseExpr(List<Tok> tokens) {
        if (tokens.isEmpty()) throw new ParseError("biểu thức rỗng");
        List<ExprPart> parts = new ArrayList<>();
        boolean expectOperand = true;
        for (Tok t : tokens) {
            if (expectOperand) {
                switch (t.kind) {
                    case STRING -> parts.add(new ExprPart(PartKind.STRING, t.value));
                    case NUMBER -> {
                        try {
                            if (t.value.contains(".")) {
                                parts.add(new ExprPart(PartKind.NUMBER, Double.parseDouble(t.value)));
                            } else {
                                parts.add(new ExprPart(PartKind.NUMBER, Long.parseLong(t.value)));
                            }
                        } catch (NumberFormatException nfe) {
                            throw new ParseError("số không hợp lệ: " + t.value);
                        }
                    }
                    case IDENT -> {
                        if ("true".equals(t.value) || "false".equals(t.value)) {
                            parts.add(new ExprPart(PartKind.BOOL, Boolean.parseBoolean(t.value)));
                        } else if ("null".equals(t.value)) {
                            parts.add(new ExprPart(PartKind.NULL, null));
                        } else if (PATH_RE.matcher(t.value).matches()) {
                            parts.add(new ExprPart(PartKind.REF, t.value));
                        } else {
                            throw new ParseError("tham chiếu không hợp lệ: " + t.value);
                        }
                    }
                    default -> throw new ParseError("mong toán hạng, gặp '" + t.value + "'");
                }
                expectOperand = false;
            } else {
                if (t.kind != TokKind.PLUS) throw new ParseError("mong '+', gặp '" + t.value + "'");
                expectOperand = true;
            }
        }
        if (expectOperand) throw new ParseError("biểu thức kết thúc bằng '+', thiếu toán hạng");
        return parts;
    }
}
