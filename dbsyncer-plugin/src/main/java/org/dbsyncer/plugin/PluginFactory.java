package org.dbsyncer.plugin;

import org.apache.commons.io.FileUtils;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.plugin.config.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/10/1 13:26
 */
@Component
public class PluginFactory {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 插件路径dbsyncer/plugin/
     */
    private final String PLUGIN_PATH = new StringBuilder(System.getProperty("user.dir")).append(File.separatorChar).append("plugins")
            .append(File.separatorChar).toString();

    private final List<Plugin> plugins = new LinkedList<>();

    public void loadPlugins() {
        Collection<File> files = FileUtils.listFiles(new File(PLUGIN_PATH), new String[] {"jar"}, true);
        if (!CollectionUtils.isEmpty(files)) {
            files.forEach(f -> plugins.add(new Plugin(f.getName(), f.getName())));
        }
    }

    public String getPluginPath() {
        return PLUGIN_PATH;
    }

    public List<Plugin> getPluginAll() {
        return plugins;
    }

    public void convert(Plugin plugin, List<Map<String, Object>> source, List<Map<String, Object>> target) {
        if (null != plugin) {
            // TODO 插件转换
        }
    }

    public void convert(Plugin plugin, String event, Map<String, Object> source, Map<String, Object> target) {
        if (null != plugin) {
        }
    }

}