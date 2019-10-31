package com.uddernetworks.bvchat;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;

public class ConfigManager {

    private String fileName;
    private FileConfig config;

    private String primaryToken;

    public ConfigManager(String fileName) {
        this.fileName = fileName;
    }

    public void init() {
        config = CommentedFileConfig.builder(this.fileName).autosave().build();
        config.load();
        primaryToken = config.get("token");
    }

    public FileConfig getConfig() {
        return config;
    }

    public String getPrimaryToken() {
        return primaryToken;
    }
}
