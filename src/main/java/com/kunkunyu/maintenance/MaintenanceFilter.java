package com.kunkunyu.maintenance;

import com.kunkunyu.maintenance.service.SettingConfig;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.MediaTypeServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import run.halo.app.infra.ExternalLinkProcessor;
import run.halo.app.security.AdditionalWebFilter;
import java.util.Collections;
import java.util.List;

@Component
public class MaintenanceFilter implements AdditionalWebFilter {

    private final SettingConfig settingConfig;

    private final ExternalLinkProcessor externalLinkProcessor;

    private final WebFilter delegate;

    public MaintenanceFilter(SettingConfig settingConfig,
                             ExternalLinkProcessor externalLinkProcessor) {
        this.settingConfig = settingConfig;
        this.externalLinkProcessor = externalLinkProcessor;
        this.delegate = new WechatOAuth2RedirectFilter();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return delegate.filter(exchange, chain);
    }


    private class WechatOAuth2RedirectFilter implements WebFilter {

        /**
         * 路由匹配器：拦截GET请求，排除指定路由和静态资源，只匹配HTML内容类型
         * 拦截规则：GET请求 (/**, /)
         * 排除规则：
         *   - /favicon.*, /login/**, /signup/**, /password-reset/**, /challenges/**, /oauth2/**, /social/**
         *   - 静态资源：/console/assets/**, /uc/assets/**, /themes/{themeName}/assets/{*resourcePaths}, 
         *              /plugins/{pluginName}/assets/**, /webjars/**, /js/**, /styles/**, /halo-tracker.js, /images/**
         *   - 其他：/console/**, /uc/**, /login, /logout, /signup, /apis/**
         * 媒体类型过滤：只匹配HTML内容类型
         */
        private final ServerWebExchangeMatcher requiresMatcher;

        public WechatOAuth2RedirectFilter() {
            ServerWebExchangeMatcher getMatcher = ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, "/**", "/");
            
            ServerWebExchangeMatcher excludeMatcher = new OrServerWebExchangeMatcher(
                ServerWebExchangeMatchers.pathMatchers("/favicon.*"),
                ServerWebExchangeMatchers.pathMatchers("/login/**"),
                ServerWebExchangeMatchers.pathMatchers("/signup/**"),
                ServerWebExchangeMatchers.pathMatchers("/password-reset/**"),
                ServerWebExchangeMatchers.pathMatchers("/challenges/**"),
                ServerWebExchangeMatchers.pathMatchers("/oauth2/**"),
                ServerWebExchangeMatchers.pathMatchers("/social/**"),
                ServerWebExchangeMatchers.pathMatchers("/console/assets/**"),
                ServerWebExchangeMatchers.pathMatchers("/uc/assets/**"),
                ServerWebExchangeMatchers.pathMatchers("/themes/{themeName}/assets/{*resourcePaths}"),
                ServerWebExchangeMatchers.pathMatchers("/plugins/{pluginName}/assets/**"),
                ServerWebExchangeMatchers.pathMatchers("/webjars/**"),
                ServerWebExchangeMatchers.pathMatchers("/js/**"),
                ServerWebExchangeMatchers.pathMatchers("/styles/**"),
                ServerWebExchangeMatchers.pathMatchers("/halo-tracker.js"),
                ServerWebExchangeMatchers.pathMatchers("/images/**"),
                ServerWebExchangeMatchers.pathMatchers("/console/**"),
                ServerWebExchangeMatchers.pathMatchers("/uc/**"),
                ServerWebExchangeMatchers.pathMatchers("/login"),
                ServerWebExchangeMatchers.pathMatchers("/logout"),
                ServerWebExchangeMatchers.pathMatchers("/signup"),
                ServerWebExchangeMatchers.pathMatchers("/apis/**"),
                ServerWebExchangeMatchers.pathMatchers("/maintenance")
            );
            
            MediaTypeServerWebExchangeMatcher htmlMatcher = new MediaTypeServerWebExchangeMatcher(MediaType.TEXT_HTML);
            htmlMatcher.setIgnoredMediaTypes(Collections.singleton(MediaType.ALL));
            
            this.requiresMatcher = new AndServerWebExchangeMatcher(
                getMatcher,
                new NegatedServerWebExchangeMatcher(excludeMatcher),
                htmlMatcher
            );
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            return requiresMatcher.matches(exchange)
                .flatMap(matchResult -> {
                    BasicConfig basicConfig = settingConfig.getBasicConfig();
                    if (!basicConfig.getEnabled()) {
                        return chain.filter(exchange);
                    }

                    // 未匹配路径，继续过滤器链
                    if (!matchResult.isMatch()) {
                        return chain.filter(exchange);
                    }

                    return exchange.getSession()
                        .map(session -> {
                            Object springSecurityContext = session.getAttribute("SPRING_SECURITY_CONTEXT");
                            if (springSecurityContext instanceof SecurityContext) {
                                SecurityContext context = (SecurityContext) springSecurityContext;
                                if (context.getAuthentication() != null) {
                                    return context.getAuthentication().getName();
                                }
                            }
                            return "anonymousUser";
                        })
                        .defaultIfEmpty("anonymousUser")
                        .flatMap(username -> {
                            String whitelistMode = basicConfig.getWhitelistMode();
                            if (whitelistMode.equals("login") && !username.equals("anonymousUser")) {
                                return chain.filter(exchange);
                            }

                            if (whitelistMode.equals("user") && basicConfig.getWhitelist().contains(username)) {
                                return chain.filter(exchange);
                            }

                            boolean shouldRedirect = shouldRedirectToMaintenance(basicConfig);
                            if (shouldRedirect) {
                                return redirectToMaintenance(exchange);
                            }

                            return chain.filter(exchange);
                        });

                });
        }
    }

    /**
     * 判断是否应该重定向到维护页面
     * @param basicConfig 基础配置
     * @return true 表示需要重定向，false 表示不需要
     */
    private boolean shouldRedirectToMaintenance(BasicConfig basicConfig) {
        String maintenanceType = basicConfig.getMaintenanceType();
        
        if ("always".equals(maintenanceType)) {
            return true;
        }
        
        if ("timing".equals(maintenanceType)) {
            Boolean repetitionCycle = basicConfig.getRepetitionCycle();
            
            if (repetitionCycle != null && repetitionCycle) {
                return isInRepetitionCycle(basicConfig);
            } else {
                return isInTimingRange(basicConfig);
            }
        }
        
        return false;
    }

    /**
     * 判断当前时间是否在定时维护范围内
     * @param basicConfig 基础配置
     * @return true 表示在维护范围内
     */
    private boolean isInTimingRange(BasicConfig basicConfig) {
        try {
            String startTime = basicConfig.getStartTime();
            String endTime = basicConfig.getEndTime();
            
            if (startTime == null || endTime == null) {
                return false;
            }
            
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime start = java.time.LocalDateTime.parse(startTime);
            java.time.LocalDateTime end = java.time.LocalDateTime.parse(endTime);
            
            return now.isAfter(start) && now.isBefore(end);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断当前时间是否在重复周期维护范围内
     * @param basicConfig 基础配置
     * @return true 表示在维护范围内
     */
    private boolean isInRepetitionCycle(BasicConfig basicConfig) {
        try {
            List<String> cycle = basicConfig.getCycle();
            String cycleStartTime = basicConfig.getCycleStartTime();
            String cycleEndTime = basicConfig.getCycleEndTime();
            
            if (cycle == null || cycle.isEmpty() || cycleStartTime == null || cycleEndTime == null) {
                return false;
            }
            
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.DayOfWeek dayOfWeek = now.getDayOfWeek();
            int currentDay = dayOfWeek.getValue();
            
            if (!cycle.contains(String.valueOf(currentDay))) {
                return false;
            }
            
            java.time.LocalTime currentTime = now.toLocalTime();
            java.time.LocalTime startTime = java.time.LocalTime.parse(cycleStartTime);
            java.time.LocalTime endTime = java.time.LocalTime.parse(cycleEndTime);
            
            return currentTime.isAfter(startTime) && currentTime.isBefore(endTime);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 重定向到维护页面
     * @param exchange 服务器交换对象
     * @return Mono<Void>
     */
    private Mono<Void> redirectToMaintenance(ServerWebExchange exchange) {
        String maintenanceUrl = externalLinkProcessor.processLink("/maintenance");
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(java.net.URI.create(maintenanceUrl));
        return exchange.getResponse().setComplete();
    }

}
