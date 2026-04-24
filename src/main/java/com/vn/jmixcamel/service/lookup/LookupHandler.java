package com.vn.jmixcamel.service.lookup;

import java.util.List;
import java.util.Map;

public interface LookupHandler {

    String type();

    Object lookup(List<String> by, Map<String, Object> extracted);
}
