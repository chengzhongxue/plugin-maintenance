package com.kunkunyu.maintenance;

import com.kunkunyu.maintenance.service.SettingConfig;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class MaintenancePlugin extends BasePlugin {

    private final SettingConfig settingConfig;

    public MaintenancePlugin(PluginContext pluginContext, SettingConfig settingConfig) {
        super(pluginContext);
        this.settingConfig = settingConfig;
    }

    @Override
    public void start() {
        settingConfig.setConfig();
    }

    @Override
    public void stop() {
    }
}
