package com.kunkunyu.maintenance.theme;

import com.kunkunyu.maintenance.BasicConfig;
import com.kunkunyu.maintenance.service.SettingConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import run.halo.app.theme.TemplateNameResolver;
import run.halo.app.theme.router.ModelConst;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Component
@RequiredArgsConstructor
public class MaintenanceRouter {

    private final TemplateNameResolver templateNameResolver;

    private final SettingConfig settingConfig;

    @Bean
    RouterFunction<ServerResponse> momentRouterFunction() {
        return route(GET("/maintenance"), handlerThemeFunction());
    }


    private HandlerFunction<ServerResponse> handlerThemeFunction() {
        return request -> templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "maintenance")
            .flatMap(templateName -> {
                BasicConfig basicConfig = settingConfig.getBasicConfig();
                Map<String, Object> model = new HashMap<>();
                model.put("title", basicConfig.getTitle());
                model.put("description", basicConfig.getDescription());
                model.put(ModelConst.TEMPLATE_ID, "maintenance");

                return ServerResponse.ok().render(templateName, model);
            });
    }
}
