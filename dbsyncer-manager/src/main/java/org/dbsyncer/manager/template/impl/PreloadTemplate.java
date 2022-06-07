package org.dbsyncer.manager.template.impl;

import com.alibaba.fastjson.JSONObject;
import org.dbsyncer.common.model.Paging;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.manager.Manager;
import org.dbsyncer.manager.config.OperationConfig;
import org.dbsyncer.manager.config.PreloadCallBack;
import org.dbsyncer.manager.config.QueryConfig;
import org.dbsyncer.manager.enums.HandlerEnum;
import org.dbsyncer.manager.template.Handler;
import org.dbsyncer.manager.template.impl.OperationTemplate.Group;
import org.dbsyncer.parser.Parser;
import org.dbsyncer.parser.enums.MetaEnum;
import org.dbsyncer.parser.model.ConfigModel;
import org.dbsyncer.parser.model.Mapping;
import org.dbsyncer.parser.model.Meta;
import org.dbsyncer.storage.StorageService;
import org.dbsyncer.storage.constant.ConfigConstant;
import org.dbsyncer.storage.enums.StorageEnum;
import org.dbsyncer.storage.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 预加载配置模板
 *
 * @author AE86
 * @version 1.0.0
 * @date 2019/9/16 23:59
 */
@Component
public final class PreloadTemplate implements ApplicationListener<ContextRefreshedEvent> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private Parser parser;

    @Autowired
    private Manager manager;

    @Autowired
    private StorageService storageService;

    @Autowired
    private OperationTemplate operationTemplate;

    public void execute(HandlerEnum handlerEnum) {
        Query query = new Query();
        query.setType(StorageEnum.CONFIG);
        String modelType = handlerEnum.getModelType();
        query.addFilter(ConfigConstant.CONFIG_MODEL_TYPE, modelType);

        int pageNum = 1;
        int pageSize = 20;
        long total = 0;
        for (; ; ) {
            query.setPageNum(pageNum);
            query.setPageSize(pageSize);
            Paging paging = storageService.query(query);
            List<Map> data = (List<Map>) paging.getData();
            if (CollectionUtils.isEmpty(data)) {
                break;
            }
            Handler handler = handlerEnum.getHandler();
            data.forEach(map -> {
                String json = (String) map.get(ConfigConstant.CONFIG_MODEL_JSON);
                ConfigModel model = (ConfigModel) handler.execute(new PreloadCallBack(parser, json));
                if (null != model) {
                    operationTemplate.cache(model, handlerEnum.getGroupStrategyEnum());
                }
            });
            total += paging.getTotal();
            pageNum++;
        }

        logger.info("PreLoad {}:{}", modelType, total);
    }

    public void reload(String json) {
        Map<String, JSONObject> map = JsonUtil.jsonToObj(json, Map.class);
        if (CollectionUtils.isEmpty(map)) {
            return;
        }

        // Load configs
        reload(map, HandlerEnum.PRELOAD_CONFIG);
        // Load connectors
        reload(map, HandlerEnum.PRELOAD_CONNECTOR);
        // Load mappings
        reload(map, HandlerEnum.PRELOAD_MAPPING);
        // Load metas
        reload(map, HandlerEnum.PRELOAD_META);

        launch();
    }

    private void reload(Map<String, JSONObject> map, HandlerEnum handlerEnum) {
        reload(map, handlerEnum, handlerEnum.getModelType());
    }

    private void reload(Map<String, JSONObject> map, HandlerEnum handlerEnum, String groupId) {
        JSONObject config = map.get(groupId);
        Group group = JsonUtil.jsonToObj(config.toJSONString(), Group.class);
        if (null == group) {
            return;
        }

        List<String> index = group.getIndex();
        if (CollectionUtils.isEmpty(index)) {
            return;
        }

        Handler handler = handlerEnum.getHandler();
        for (String e : index) {
            JSONObject m = map.get(e);
            ConfigModel model = (ConfigModel) handler.execute(new PreloadCallBack(parser, m.toJSONString()));
            operationTemplate.execute(new OperationConfig(model, HandlerEnum.OPR_ADD, handlerEnum.getGroupStrategyEnum()));
            // Load tableGroups
            if (HandlerEnum.PRELOAD_MAPPING == handlerEnum) {
                handlerEnum = HandlerEnum.PRELOAD_TABLE_GROUP;
                reload(map, handlerEnum, operationTemplate.getGroupId(model, handlerEnum.getGroupStrategyEnum()));
            }
        }
    }

    private void launch() {
        Meta meta = new Meta();
        meta.setType(ConfigConstant.META);
        QueryConfig<Meta> queryConfig = new QueryConfig<>(meta);
        List<Meta> metas = operationTemplate.queryAll(queryConfig);
        if (!CollectionUtils.isEmpty(metas)) {
            metas.forEach(m -> {
                // 恢复驱动状态
                if (MetaEnum.RUNNING.getCode() == m.getState()) {
                    Mapping mapping = manager.getMapping(m.getMappingId());
                    manager.start(mapping);
                } else if (MetaEnum.STOPPING.getCode() == m.getState()) {
                    manager.changeMetaState(m.getId(), MetaEnum.READY);
                }
            });
        }
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        // Load configModels
        Arrays.stream(HandlerEnum.values()).filter(handlerEnum -> handlerEnum.isPreload()).forEach(handlerEnum -> execute(handlerEnum));

        // Load plugins
        manager.loadPlugins();

        // Check connectors status
        manager.checkAllConnectorStatus();

        // Launch drivers
        launch();
    }

}