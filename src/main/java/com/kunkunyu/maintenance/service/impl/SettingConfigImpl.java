package com.kunkunyu.maintenance.service.impl;


import com.kunkunyu.maintenance.BasicConfig;
import com.kunkunyu.maintenance.service.SettingConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.PluginConfigUpdatedEvent;
import run.halo.app.plugin.ReactiveSettingFetcher;
import java.util.concurrent.atomic.AtomicReference;


@Component
@RequiredArgsConstructor
public class SettingConfigImpl implements SettingConfig {

    private final ReactiveSettingFetcher settingFetcher;
    private final AtomicReference<BasicConfig> basicConfigRef = new AtomicReference<>();


    @Async
    @EventListener(PluginConfigUpdatedEvent.class)
    void onPluginConfigUpdatedEvent() {
        setConfig();
    }

    public void setConfig() {
        var basicConfigMono = settingFetcher.fetch(BasicConfig.GROUP, BasicConfig.class)
            .doOnNext(basicConfigRef::set);

        Mono.when(
            basicConfigMono
        ).block();
    }


    public BasicConfig getBasicConfig() {
        BasicConfig basicConfig = basicConfigRef.get();
        if (basicConfig == null) {
            basicConfig = BasicConfig.empty();
        }
        return basicConfig;
    }

}
