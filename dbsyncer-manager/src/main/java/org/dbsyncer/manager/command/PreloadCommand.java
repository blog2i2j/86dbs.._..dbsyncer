package org.dbsyncer.manager.command;

import org.dbsyncer.manager.Command;
import org.dbsyncer.parser.Parser;
import org.dbsyncer.parser.model.*;
import org.dbsyncer.parser.model.SystemConfig;

public class PreloadCommand implements Command {

    private Parser parser;

    private String json;

    public PreloadCommand(Parser parser, String json) {
        this.parser = parser;
        this.json = json;
    }

    public Object parseSystemConfig() {
        return parser.parseObject(json, SystemConfig.class);
    }

    public Object parseUserConfig() {
        return parser.parseObject(json, UserConfig.class);
    }

    public Object parseConnector() {
        return parser.parseConnector(json);
    }

    public Object parseMapping() {
        return parser.parseObject(json, Mapping.class);
    }

    public Object parseTableGroup() {
        return parser.parseObject(json, TableGroup.class);
    }

    public Object parseMeta() {
        return parser.parseObject(json, Meta.class);
    }

    public Object parseProjectGroup() {
        return parser.parseObject(json, ProjectGroup.class);
    }

}