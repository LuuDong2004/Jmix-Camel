package com.vn.jmixcamel.rest;

import com.vn.jmixcamel.dto.RouteDeployRequest;
import com.vn.jmixcamel.dto.RouteExecuteRequest;
import com.vn.jmixcamel.service.DynamicCamelRouteService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/camel-routes")
public class CamelRouteController {
    private final DynamicCamelRouteService dynamicCamelRouteService;

    public CamelRouteController(DynamicCamelRouteService dynamicCamelRouteService) {
        this.dynamicCamelRouteService = dynamicCamelRouteService;
    }

    @PostMapping("/deploy")
    public String deploy(@RequestBody RouteDeployRequest request) {
        return dynamicCamelRouteService.deployRoute(
                request.getRouteId(),
                request.getDslType(),
                request.getContent()
        );
    }

    @PostMapping("/{routeId}/execute")
    public Object execute(
            @PathVariable String routeId,
            @RequestBody RouteExecuteRequest request
    ) {
        return dynamicCamelRouteService.executeRoute(routeId, request.getBody());
    }
}
