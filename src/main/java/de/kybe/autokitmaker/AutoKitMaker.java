package de.kybe.autokitmaker;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;


public class AutoKitMaker extends Plugin {
    @Override
    public void onLoad() {
        final AutoKitModule autoKitModule = new AutoKitModule();
        RusherHackAPI.getModuleManager().registerFeature(autoKitModule);

        final AutoKitCommand autoKitCommand = new AutoKitCommand();
        RusherHackAPI.getCommandManager().registerFeature(autoKitCommand);
    }

    @Override
    public void onUnload() {
    }
}