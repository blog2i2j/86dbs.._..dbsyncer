/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.elasticsearch.api;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.lucene.util.BytesRef;
import org.dbsyncer.common.util.StringUtil;
import org.elasticsearch.Version;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.storedscripts.DeleteStoredScriptRequest;
import org.elasticsearch.action.admin.cluster.storedscripts.GetStoredScriptRequest;
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.explain.ExplainRequest;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.GetSourceRequest;
import org.elasticsearch.client.core.MultiTermVectorsRequest;
import org.elasticsearch.client.core.TermVectorsRequest;
import org.elasticsearch.client.indices.AnalyzeRequest;
import org.elasticsearch.client.security.RefreshPolicy;
import org.elasticsearch.client.tasks.TaskId;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.SuppressForbidden;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.lucene.uid.Versions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.CollectionUtils;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.rankeval.RankEvalRequest;
import org.elasticsearch.index.reindex.AbstractBulkByScrollRequest;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.index.seqno.SequenceNumbers;
import org.elasticsearch.rest.action.search.RestSearchAction;
import org.elasticsearch.script.mustache.MultiSearchTemplateRequest;
import org.elasticsearch.script.mustache.SearchTemplateRequest;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * ES请求（同时兼容6-7版本API）
 *
 * @Author AE86
 * @Version 1.0.0
 * @Date 2023-11-25 23:10
 */
final class RequestConverters {
    static final XContentType REQUEST_BODY_CONTENT_TYPE = XContentType.JSON;

    private RequestConverters() {
        // Contains only status utility methods
    }

    static Request delete(DeleteRequest deleteRequest) {
        String endpoint = endpoint(deleteRequest.index(), deleteRequest.type(), deleteRequest.id());
        Request request = new Request(HttpDelete.METHOD_NAME, endpoint);

        RequestConverters.Params parameters = new RequestConverters.Params();
        parameters.withRouting(deleteRequest.routing());
        parameters.withTimeout(deleteRequest.timeout());
        parameters.withVersion(deleteRequest.version());
        parameters.withVersionType(deleteRequest.versionType());
        parameters.withIfSeqNo(deleteRequest.ifSeqNo());
        parameters.withIfPrimaryTerm(deleteRequest.ifPrimaryTerm());
        parameters.withRefreshPolicy(deleteRequest.getRefreshPolicy());
        parameters.withWaitForActiveShards(deleteRequest.waitForActiveShards());
        request.addParameters(parameters.asMap());
        return request;
    }

    static Request info() {
        return new Request(HttpGet.METHOD_NAME, "/");
    }

    static Request bulk(BulkRequest bulkRequest, Version version) throws IOException {
        Request request = new Request(HttpPost.METHOD_NAME, "/_bulk");

        RequestConverters.Params parameters = new RequestConverters.Params();
        parameters.withTimeout(bulkRequest.timeout());
        parameters.withRefreshPolicy(bulkRequest.getRefreshPolicy());
        parameters.withPipeline(bulkRequest.pipeline());
        parameters.withRouting(bulkRequest.routing());
        // Bulk API only supports newline delimited JSON or Smile. Before executing
        // the bulk, we need to check that all requests have the same content-type
        // and this content-type is supported by the Bulk API.
        XContentType bulkContentType = null;
        for (int i = 0; i < bulkRequest.numberOfActions(); i++) {
            DocWriteRequest<?> action = bulkRequest.requests().get(i);

            DocWriteRequest.OpType opType = action.opType();
            if (opType == DocWriteRequest.OpType.INDEX || opType == DocWriteRequest.OpType.CREATE) {
                bulkContentType = enforceSameContentType((IndexRequest) action, bulkContentType);

            } else if (opType == DocWriteRequest.OpType.UPDATE) {
                UpdateRequest updateRequest = (UpdateRequest) action;
                if (updateRequest.doc() != null) {
                    bulkContentType = enforceSameContentType(updateRequest.doc(), bulkContentType);
                }
                if (updateRequest.upsertRequest() != null) {
                    bulkContentType = enforceSameContentType(updateRequest.upsertRequest(), bulkContentType);
                }
            }
        }

        if (bulkContentType == null) {
            bulkContentType = XContentType.JSON;
        }

        final byte separator = bulkContentType.xContent().streamSeparator();
        final ContentType requestContentType = createContentType(bulkContentType);

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        for (DocWriteRequest<?> action : bulkRequest.requests()) {
            DocWriteRequest.OpType opType = action.opType();

            try (XContentBuilder metadata = XContentBuilder.builder(bulkContentType.xContent())) {
                metadata.startObject();
                {
                    metadata.startObject(opType.getLowercase());
                    if (Strings.hasLength(action.index())) {
                        metadata.field("_index", action.index());
                    }
                    // 8.x 版本已弃用
                    if (EasyVersion.V_8_0_0.after(version) && Strings.hasLength(action.type())) {
                        metadata.field("_type", action.type());
                    }
                    if (Strings.hasLength(action.id())) {
                        metadata.field("_id", action.id());
                    }
                    if (Strings.hasLength(action.routing())) {
                        metadata.field("routing", action.routing());
                    }
                    if (action.version() != Versions.MATCH_ANY) {
                        metadata.field("version", action.version());
                    }

                    VersionType versionType = action.versionType();
                    if (versionType != VersionType.INTERNAL) {
                        if (versionType == VersionType.EXTERNAL) {
                            metadata.field("version_type", "external");
                        } else if (versionType == VersionType.EXTERNAL_GTE) {
                            metadata.field("version_type", "external_gte");
                        }
                    }

                    if (action.ifSeqNo() != SequenceNumbers.UNASSIGNED_SEQ_NO) {
                        metadata.field("if_seq_no", action.ifSeqNo());
                        metadata.field("if_primary_term", action.ifPrimaryTerm());
                    }

                    if (opType == DocWriteRequest.OpType.INDEX || opType == DocWriteRequest.OpType.CREATE) {
                        IndexRequest indexRequest = (IndexRequest) action;
                        if (Strings.hasLength(indexRequest.getPipeline())) {
                            metadata.field("pipeline", indexRequest.getPipeline());
                        }
                    } else if (opType == DocWriteRequest.OpType.UPDATE) {
                        UpdateRequest updateRequest = (UpdateRequest) action;
                        if (updateRequest.retryOnConflict() > 0) {
                            metadata.field("retry_on_conflict", updateRequest.retryOnConflict());
                        }
                        if (updateRequest.fetchSource() != null) {
                            metadata.field("_source", updateRequest.fetchSource());
                        }
                    }
                    metadata.endObject();
                }
                metadata.endObject();

                BytesRef metadataSource = BytesReference.bytes(metadata).toBytesRef();
                content.write(metadataSource.bytes, metadataSource.offset, metadataSource.length);
                content.write(separator);
            }

            BytesRef source = null;
            if (opType == DocWriteRequest.OpType.INDEX || opType == DocWriteRequest.OpType.CREATE) {
                IndexRequest indexRequest = (IndexRequest) action;
                BytesReference indexSource = indexRequest.source();
                XContentType indexXContentType = indexRequest.getContentType();

                try (XContentParser parser = XContentHelper.createParser(
                        /*
                         * EMPTY and THROW are fine here because we just call
                         * copyCurrentStructure which doesn't touch the
                         * registry or deprecation.
                         */
                        NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                        indexSource, indexXContentType)) {
                    try (XContentBuilder builder = XContentBuilder.builder(bulkContentType.xContent())) {
                        builder.copyCurrentStructure(parser);
                        source = BytesReference.bytes(builder).toBytesRef();
                    }
                }
            } else if (opType == DocWriteRequest.OpType.UPDATE) {
                source = XContentHelper.toXContent((UpdateRequest) action, bulkContentType, false).toBytesRef();
            }

            if (source != null) {
                content.write(source.bytes, source.offset, source.length);
                content.write(separator);
            }
        }
        request.addParameters(parameters.asMap());
        request.setEntity(new NByteArrayEntity(content.toByteArray(), 0, content.size(), requestContentType));
        return request;
    }

    static Request exists(GetRequest getRequest) {
        return getStyleRequest(HttpHead.METHOD_NAME, getRequest);
    }

    static Request get(GetRequest getRequest) {
        return getStyleRequest(HttpGet.METHOD_NAME, getRequest);
    }

    private static Request getStyleRequest(String method, GetRequest getRequest) {
        Request request = new Request(method, endpoint(getRequest.index(), getRequest.type(), getRequest.id()));

        RequestConverters.Params parameters = new RequestConverters.Params();
        parameters.withPreference(getRequest.preference());
        parameters.withRouting(getRequest.routing());
        parameters.withRefresh(getRequest.refresh());
        parameters.withRealtime(getRequest.realtime());
        parameters.withStoredFields(getRequest.storedFields());
        parameters.withVersion(getRequest.version());
        parameters.withVersionType(getRequest.versionType());
        parameters.withFetchSourceContext(getRequest.fetchSourceContext());
        request.addParameters(parameters.asMap());
        return request;
    }

    static Request sourceExists(GetSourceRequest getSourceRequest) {
        return sourceRequest(getSourceRequest, HttpHead.METHOD_NAME);
    }

    static Request getSource(GetSourceRequest getSourceRequest) {
        return sourceRequest(getSourceRequest, HttpGet.METHOD_NAME);
    }

    private static Request sourceRequest(GetSourceRequest getSourceRequest, String httpMethodName) {
        RequestConverters.Params parameters = new RequestConverters.Params();
        parameters.withPreference(getSourceRequest.preference());
        parameters.withRouting(getSourceRequest.routing());
        parameters.withRefresh(getSourceRequest.refresh());
        parameters.withRealtime(getSourceRequest.realtime());
        parameters.withFetchSourceContext(getSourceRequest.fetchSourceContext());

        String optionalType = getSourceRequest.type();
        String endpoint;
        if (optionalType == null) {
            endpoint = endpoint(getSourceRequest.index(), "_source", getSourceRequest.id());
        } else {
            endpoint = endpoint(getSourceRequest.index(), optionalType, getSourceRequest.id(), "_source");
        }
        Request request = new Request(httpMethodName, endpoint);
        request.addParameters(parameters.asMap());
        return request;
    }

    static Request multiGet(MultiGetRequest multiGetRequest) throws IOException {
        Request request = new Request(HttpPost.METHOD_NAME, "/_mget");

        RequestConverters.Params parameters = new RequestConverters.Params();
        parameters.withPreference(multiGetRequest.preference());
        parameters.withRealtime(multiGetRequest.realtime());
        parameters.withRefresh(multiGetRequest.refresh());
        request.addParameters(parameters.asMap());
        request.setEntity(createEntity(multiGetRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request index(IndexRequest indexRequest) {
        String method = Strings.hasLength(indexRequest.id()) ? HttpPut.METHOD_NAME : HttpPost.METHOD_NAME;

        String endpoint;
        if (indexRequest.opType() == DocWriteRequest.OpType.CREATE) {
            endpoint = indexRequest.type().equals(MapperService.SINGLE_MAPPING_NAME)
                    ? endpoint(indexRequest.index(), "_create", indexRequest.id())
                    : endpoint(indexRequest.index(), indexRequest.type(), indexRequest.id(), "_create");
        } else {
            endpoint = endpoint(indexRequest.index(), indexRequest.type(), indexRequest.id());
        }

        Request request = new Request(method, endpoint);

        RequestConverters.Params parameters = new RequestConverters.Params();
        parameters.withRouting(indexRequest.routing());
        parameters.withTimeout(indexRequest.timeout());
        parameters.withVersion(indexRequest.version());
        parameters.withVersionType(indexRequest.versionType());
        parameters.withIfSeqNo(indexRequest.ifSeqNo());
        parameters.withIfPrimaryTerm(indexRequest.ifPrimaryTerm());
        parameters.withPipeline(indexRequest.getPipeline());
        parameters.withRefreshPolicy(indexRequest.getRefreshPolicy());
        parameters.withWaitForActiveShards(indexRequest.waitForActiveShards());

        BytesRef source = indexRequest.source().toBytesRef();
        ContentType contentType = createContentType(indexRequest.getContentType());
        request.addParameters(parameters.asMap());
        request.setEntity(new NByteArrayEntity(source.bytes, source.offset, source.length, contentType));
        return request;
    }

    static Request ping() {
        return new Request(HttpHead.METHOD_NAME, "/");
    }

    static Request update(UpdateRequest updateRequest) throws IOException {
        String endpoint = updateRequest.type().equals(MapperService.SINGLE_MAPPING_NAME)
                ? endpoint(updateRequest.index(), "_update", updateRequest.id())
                : endpoint(updateRequest.index(), updateRequest.type(), updateRequest.id(), "_update");
        Request request = new Request(HttpPost.METHOD_NAME, endpoint);

        RequestConverters.Params parameters = new RequestConverters.Params();
        parameters.withRouting(updateRequest.routing());
        parameters.withTimeout(updateRequest.timeout());
        parameters.withRefreshPolicy(updateRequest.getRefreshPolicy());
        parameters.withWaitForActiveShards(updateRequest.waitForActiveShards());
        parameters.withDocAsUpsert(updateRequest.docAsUpsert());
        parameters.withFetchSourceContext(updateRequest.fetchSource());
        parameters.withRetryOnConflict(updateRequest.retryOnConflict());
        parameters.withVersion(updateRequest.version());
        parameters.withVersionType(updateRequest.versionType());

        // The Java API allows update requests with different content types
        // set for the partial document and the upsert document. This client
        // only accepts update requests that have the same content types set
        // for both doc and upsert.
        XContentType xContentType = null;
        if (updateRequest.doc() != null) {
            xContentType = updateRequest.doc().getContentType();
        }
        if (updateRequest.upsertRequest() != null) {
            XContentType upsertContentType = updateRequest.upsertRequest().getContentType();
            if ((xContentType != null) && (xContentType != upsertContentType)) {
                throw new IllegalStateException("Update request cannot have different content types for doc [" + xContentType + "]" +
                        " and upsert [" + upsertContentType + "] documents");
            } else {
                xContentType = upsertContentType;
            }
        }
        if (xContentType == null) {
            xContentType = Requests.INDEX_CONTENT_TYPE;
        }
        request.addParameters(parameters.asMap());
        request.setEntity(createEntity(updateRequest, xContentType));
        return request;
    }

    /**
     * Convert a {@linkplain SearchRequest} into a {@linkplain Request}.
     * @param searchRequest the request to convert
     * @param searchEndpoint the name of the search endpoint. {@literal _search}
     *    for standard searches and {@literal _rollup_search} for rollup
     *    searches.
     */
    static Request search(SearchRequest searchRequest, String searchEndpoint, Version version) throws IOException {
        Request request = new Request(HttpPost.METHOD_NAME, endpoint(searchRequest.indices(), searchRequest.types(), searchEndpoint));

        RequestConverters.Params params = new RequestConverters.Params();
        addSearchRequestParams(params, searchRequest, version);

        if (searchRequest.source() != null) {
            request.setEntity(createEntity(searchRequest.source(), REQUEST_BODY_CONTENT_TYPE));
        }
        request.addParameters(params.asMap());
        return request;
    }

    static void addSearchRequestParams(RequestConverters.Params params, SearchRequest searchRequest, Version version) {
        params.putParam(RestSearchAction.TYPED_KEYS_PARAM, "true");
        params.withRouting(searchRequest.routing());
        params.withPreference(searchRequest.preference());
        params.withIndicesOptions(searchRequest.indicesOptions(), version);
        params.withSearchType(searchRequest.searchType().name().toLowerCase(Locale.ROOT));
        // 7.x 版本以上支持该参数
        if (EasyVersion.V_7_0_0.onOrBefore(version)) {
            params.putParam("ccs_minimize_roundtrips", Boolean.toString(searchRequest.isCcsMinimizeRoundtrips()));
        }
        if (searchRequest.getPreFilterShardSize() != null) {
            params.putParam("pre_filter_shard_size", Integer.toString(searchRequest.getPreFilterShardSize()));
        }
        params.withMaxConcurrentShardRequests(searchRequest.getMaxConcurrentShardRequests());
        if (searchRequest.requestCache() != null) {
            params.withRequestCache(searchRequest.requestCache());
        }
        if (searchRequest.allowPartialSearchResults() != null) {
            params.withAllowPartialResults(searchRequest.allowPartialSearchResults());
        }
        params.withBatchedReduceSize(searchRequest.getBatchedReduceSize());
        if (searchRequest.scroll() != null) {
            params.putParam("scroll", searchRequest.scroll().keepAlive());
        }
    }

    static Request searchScroll(SearchScrollRequest searchScrollRequest) throws IOException {
        Request request = new Request(HttpPost.METHOD_NAME, "/_search/scroll");
        request.setEntity(createEntity(searchScrollRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request clearScroll(ClearScrollRequest clearScrollRequest) throws IOException {
        Request request = new Request(HttpDelete.METHOD_NAME, "/_search/scroll");
        request.setEntity(createEntity(clearScrollRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request multiSearch(MultiSearchRequest multiSearchRequest) throws IOException {
        Request request = new Request(HttpPost.METHOD_NAME, "/_msearch");

        RequestConverters.Params params = new RequestConverters.Params();
        params.putParam(RestSearchAction.TYPED_KEYS_PARAM, "true");
        if (multiSearchRequest.maxConcurrentSearchRequests() != MultiSearchRequest.MAX_CONCURRENT_SEARCH_REQUESTS_DEFAULT) {
            params.putParam("max_concurrent_searches", Integer.toString(multiSearchRequest.maxConcurrentSearchRequests()));
        }

        XContent xContent = REQUEST_BODY_CONTENT_TYPE.xContent();
        byte[] source = MultiSearchRequest.writeMultiLineFormat(multiSearchRequest, xContent);
        request.addParameters(params.asMap());
        request.setEntity(new NByteArrayEntity(source, createContentType(xContent.type())));
        return request;
    }

    static Request searchTemplate(SearchTemplateRequest searchTemplateRequest, Version version) throws IOException {
        Request request;

        if (searchTemplateRequest.isSimulate()) {
            request = new Request(HttpGet.METHOD_NAME, "_render/template");
        } else {
            SearchRequest searchRequest = searchTemplateRequest.getRequest();
            String endpoint = endpoint(searchRequest.indices(), searchRequest.types(), "_search/template");
            request = new Request(HttpGet.METHOD_NAME, endpoint);

            RequestConverters.Params params = new RequestConverters.Params();
            addSearchRequestParams(params, searchRequest, version);
            request.addParameters(params.asMap());
        }

        request.setEntity(createEntity(searchTemplateRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request multiSearchTemplate(MultiSearchTemplateRequest multiSearchTemplateRequest) throws IOException {
        Request request = new Request(HttpPost.METHOD_NAME, "/_msearch/template");

        RequestConverters.Params params = new RequestConverters.Params();
        params.putParam(RestSearchAction.TYPED_KEYS_PARAM, "true");
        if (multiSearchTemplateRequest.maxConcurrentSearchRequests() != MultiSearchRequest.MAX_CONCURRENT_SEARCH_REQUESTS_DEFAULT) {
            params.putParam("max_concurrent_searches", Integer.toString(multiSearchTemplateRequest.maxConcurrentSearchRequests()));
        }
        request.addParameters(params.asMap());

        XContent xContent = REQUEST_BODY_CONTENT_TYPE.xContent();
        byte[] source = MultiSearchTemplateRequest.writeMultiLineFormat(multiSearchTemplateRequest, xContent);
        request.setEntity(new NByteArrayEntity(source, createContentType(xContent.type())));
        return request;
    }

    static Request count(CountRequest countRequest, Version version) throws IOException {
        Request request = new Request(HttpPost.METHOD_NAME, endpoint(countRequest.indices(), countRequest.types(), "_count"));
        RequestConverters.Params params = new RequestConverters.Params();
        params.withRouting(countRequest.routing());
        params.withPreference(countRequest.preference());
        params.withIndicesOptions(countRequest.indicesOptions(), version);
        if (countRequest.terminateAfter() != 0){
            params.withTerminateAfter(countRequest.terminateAfter());
        }
        if (countRequest.minScore() != null){
            params.putParam("min_score", String.valueOf(countRequest.minScore()));
        }
        request.addParameters(params.asMap());
        request.setEntity(createEntity(countRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request explain(ExplainRequest explainRequest) throws IOException {
        String endpoint = explainRequest.type().equals(MapperService.SINGLE_MAPPING_NAME)
                ? endpoint(explainRequest.index(), "_explain", explainRequest.id())
                : endpoint(explainRequest.index(), explainRequest.type(), explainRequest.id(), "_explain");
        Request request = new Request(HttpGet.METHOD_NAME, endpoint);

        RequestConverters.Params params = new RequestConverters.Params();
        params.withStoredFields(explainRequest.storedFields());
        params.withFetchSourceContext(explainRequest.fetchSourceContext());
        params.withRouting(explainRequest.routing());
        params.withPreference(explainRequest.preference());
        request.addParameters(params.asMap());
        request.setEntity(createEntity(explainRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request fieldCaps(FieldCapabilitiesRequest fieldCapabilitiesRequest, Version version) throws IOException {
        String methodName = fieldCapabilitiesRequest.indexFilter() != null ? HttpPost.METHOD_NAME  : HttpGet.METHOD_NAME;
        Request request = new Request(methodName, endpoint(fieldCapabilitiesRequest.indices(), "_field_caps"));

        RequestConverters.Params params = new RequestConverters.Params();
        params.withFields(fieldCapabilitiesRequest.fields());
        params.withIndicesOptions(fieldCapabilitiesRequest.indicesOptions(), version);
        request.addParameters(params.asMap());
        if (fieldCapabilitiesRequest.indexFilter() != null) {
            request.setEntity(createEntity(fieldCapabilitiesRequest, REQUEST_BODY_CONTENT_TYPE));
        }
        return request;
    }

    static Request rankEval(RankEvalRequest rankEvalRequest, Version version) throws IOException {
        Request request = new Request(HttpGet.METHOD_NAME, endpoint(rankEvalRequest.indices(), Strings.EMPTY_ARRAY, "_rank_eval"));

        RequestConverters.Params params = new RequestConverters.Params();
        params.withIndicesOptions(rankEvalRequest.indicesOptions(), version);
        params.putParam("search_type", rankEvalRequest.searchType().name().toLowerCase(Locale.ROOT));
        request.addParameters(params.asMap());
        request.setEntity(createEntity(rankEvalRequest.getRankEvalSpec(), REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request reindex(ReindexRequest reindexRequest) throws IOException {
        return prepareReindexRequest(reindexRequest, true);
    }

    static Request submitReindex(ReindexRequest reindexRequest) throws IOException {
        return prepareReindexRequest(reindexRequest, false);
    }

    static Request deleteByQuery(DeleteByQueryRequest deleteByQueryRequest, Version version) throws IOException {
        return prepareDeleteByQueryRequest(deleteByQueryRequest, true, version);
    }

    static Request submitDeleteByQuery(DeleteByQueryRequest deleteByQueryRequest, Version version) throws IOException {
        return prepareDeleteByQueryRequest(deleteByQueryRequest, false, version);
    }

    static Request updateByQuery(UpdateByQueryRequest updateByQueryRequest, Version version) throws IOException {
        return prepareUpdateByQueryRequest(updateByQueryRequest, true, version);
    }

    static Request submitUpdateByQuery(UpdateByQueryRequest updateByQueryRequest, Version version) throws IOException {
        return prepareUpdateByQueryRequest(updateByQueryRequest, false, version);
    }

    private static Request prepareReindexRequest(ReindexRequest reindexRequest, boolean waitForCompletion) throws IOException {
        String endpoint = new RequestConverters.EndpointBuilder().addPathPart("_reindex").build();
        Request request = new Request(HttpPost.METHOD_NAME, endpoint);
        RequestConverters.Params params = new RequestConverters.Params()
                .withWaitForCompletion(waitForCompletion)
                .withRefresh(reindexRequest.isRefresh())
                .withTimeout(reindexRequest.getTimeout())
                .withWaitForActiveShards(reindexRequest.getWaitForActiveShards())
                .withRequestsPerSecond(reindexRequest.getRequestsPerSecond())
                .withSlices(reindexRequest.getSlices());

        if (reindexRequest.getScrollTime() != null) {
            params.putParam("scroll", reindexRequest.getScrollTime());
        }
        request.addParameters(params.asMap());
        request.setEntity(createEntity(reindexRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    private static Request prepareDeleteByQueryRequest(DeleteByQueryRequest deleteByQueryRequest, boolean waitForCompletion, Version version) throws IOException {
        String endpoint =
                endpoint(deleteByQueryRequest.indices(), deleteByQueryRequest.getDocTypes(), "_delete_by_query");
        Request request = new Request(HttpPost.METHOD_NAME, endpoint);
        RequestConverters.Params params = new RequestConverters.Params()
                .withRouting(deleteByQueryRequest.getRouting())
                .withRefresh(deleteByQueryRequest.isRefresh())
                .withTimeout(deleteByQueryRequest.getTimeout())
                .withWaitForActiveShards(deleteByQueryRequest.getWaitForActiveShards())
                .withRequestsPerSecond(deleteByQueryRequest.getRequestsPerSecond())
                .withIndicesOptions(deleteByQueryRequest.indicesOptions(), version)
                .withWaitForCompletion(waitForCompletion)
                .withSlices(deleteByQueryRequest.getSlices());
        if (deleteByQueryRequest.isAbortOnVersionConflict() == false) {
            params.putParam("conflicts", "proceed");
        }
        if (deleteByQueryRequest.getBatchSize() != AbstractBulkByScrollRequest.DEFAULT_SCROLL_SIZE) {
            params.putParam("scroll_size", Integer.toString(deleteByQueryRequest.getBatchSize()));
        }
        if (deleteByQueryRequest.getScrollTime() != AbstractBulkByScrollRequest.DEFAULT_SCROLL_TIMEOUT) {
            params.putParam("scroll", deleteByQueryRequest.getScrollTime());
        }
        if (deleteByQueryRequest.getMaxDocs() > 0) {
            params.putParam("max_docs", Integer.toString(deleteByQueryRequest.getMaxDocs()));
        }
        request.addParameters(params.asMap());
        request.setEntity(createEntity(deleteByQueryRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request prepareUpdateByQueryRequest(UpdateByQueryRequest updateByQueryRequest, boolean waitForCompletion, Version version) throws IOException {
        String endpoint =
                endpoint(updateByQueryRequest.indices(), updateByQueryRequest.getDocTypes(), "_update_by_query");
        Request request = new Request(HttpPost.METHOD_NAME, endpoint);
        RequestConverters.Params params = new RequestConverters.Params()
                .withRouting(updateByQueryRequest.getRouting())
                .withPipeline(updateByQueryRequest.getPipeline())
                .withRefresh(updateByQueryRequest.isRefresh())
                .withTimeout(updateByQueryRequest.getTimeout())
                .withWaitForActiveShards(updateByQueryRequest.getWaitForActiveShards())
                .withRequestsPerSecond(updateByQueryRequest.getRequestsPerSecond())
                .withIndicesOptions(updateByQueryRequest.indicesOptions(), version)
                .withWaitForCompletion(waitForCompletion)
                .withSlices(updateByQueryRequest.getSlices());
        if (updateByQueryRequest.isAbortOnVersionConflict() == false) {
            params.putParam("conflicts", "proceed");
        }
        if (updateByQueryRequest.getBatchSize() != AbstractBulkByScrollRequest.DEFAULT_SCROLL_SIZE) {
            params.putParam("scroll_size", Integer.toString(updateByQueryRequest.getBatchSize()));
        }
        if (updateByQueryRequest.getScrollTime() != AbstractBulkByScrollRequest.DEFAULT_SCROLL_TIMEOUT) {
            params.putParam("scroll", updateByQueryRequest.getScrollTime());
        }
        if (updateByQueryRequest.getMaxDocs() > 0) {
            params.putParam("max_docs", Integer.toString(updateByQueryRequest.getMaxDocs()));
        }
        request.addParameters(params.asMap());
        request.setEntity(createEntity(updateByQueryRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request rethrottleReindex(RethrottleRequest rethrottleRequest) {
        return rethrottle(rethrottleRequest, "_reindex");
    }

    static Request rethrottleUpdateByQuery(RethrottleRequest rethrottleRequest) {
        return rethrottle(rethrottleRequest, "_update_by_query");
    }

    static Request rethrottleDeleteByQuery(RethrottleRequest rethrottleRequest) {
        return rethrottle(rethrottleRequest, "_delete_by_query");
    }

    private static Request rethrottle(RethrottleRequest rethrottleRequest, String firstPathPart) {
        String endpoint = new RequestConverters.EndpointBuilder().addPathPart(firstPathPart).addPathPart(rethrottleRequest.getTaskId().toString())
                .addPathPart("_rethrottle").build();
        Request request = new Request(HttpPost.METHOD_NAME, endpoint);
        RequestConverters.Params params = new RequestConverters.Params()
                .withRequestsPerSecond(rethrottleRequest.getRequestsPerSecond());
        // we set "group_by" to "none" because this is the response format we can parse back
        params.putParam("group_by", "none");
        request.addParameters(params.asMap());
        return request;
    }

    static Request putScript(PutStoredScriptRequest putStoredScriptRequest) throws IOException {
        String endpoint = new RequestConverters.EndpointBuilder().addPathPartAsIs("_scripts").addPathPart(putStoredScriptRequest.id()).build();
        Request request = new Request(HttpPost.METHOD_NAME, endpoint);
        RequestConverters.Params params = new RequestConverters.Params();
        params.withTimeout(putStoredScriptRequest.timeout());
        params.withMasterTimeout(putStoredScriptRequest.masterNodeTimeout());
        if (Strings.hasText(putStoredScriptRequest.context())) {
            params.putParam("context", putStoredScriptRequest.context());
        }
        request.addParameters(params.asMap());
        request.setEntity(createEntity(putStoredScriptRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request analyze(AnalyzeRequest request) throws IOException {
        RequestConverters.EndpointBuilder builder = new RequestConverters.EndpointBuilder();
        String index = request.index();
        if (index != null) {
            builder.addPathPart(index);
        }
        builder.addPathPartAsIs("_analyze");
        Request req = new Request(HttpGet.METHOD_NAME, builder.build());
        req.setEntity(createEntity(request, REQUEST_BODY_CONTENT_TYPE));
        return req;
    }

    static Request termVectors(TermVectorsRequest tvrequest) throws IOException {
        String endpoint;
        if (tvrequest.getType() != null) {
            endpoint = new RequestConverters.EndpointBuilder().addPathPart(tvrequest.getIndex(), tvrequest.getType(), tvrequest.getId())
                    .addPathPartAsIs("_termvectors")
                    .build();
        } else {
            endpoint = new RequestConverters.EndpointBuilder().addPathPart(tvrequest.getIndex())
                    .addPathPartAsIs("_termvectors")
                    .addPathPart(tvrequest.getId())
                    .build();
        }

        Request request = new Request(HttpGet.METHOD_NAME, endpoint);
        RequestConverters.Params params = new RequestConverters.Params();
        params.withRouting(tvrequest.getRouting());
        params.withPreference(tvrequest.getPreference());
        params.withRealtime(tvrequest.getRealtime());
        request.addParameters(params.asMap());
        request.setEntity(createEntity(tvrequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request mtermVectors(MultiTermVectorsRequest mtvrequest) throws IOException {
        String endpoint = "_mtermvectors";
        Request request = new Request(HttpGet.METHOD_NAME, endpoint);
        request.setEntity(createEntity(mtvrequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    static Request getScript(GetStoredScriptRequest getStoredScriptRequest) {
        String endpoint = new RequestConverters.EndpointBuilder().addPathPartAsIs("_scripts").addPathPart(getStoredScriptRequest.id()).build();
        Request request = new Request(HttpGet.METHOD_NAME, endpoint);
        RequestConverters.Params params = new RequestConverters.Params();
        params.withMasterTimeout(getStoredScriptRequest.masterNodeTimeout());
        request.addParameters(params.asMap());
        return request;
    }

    static Request deleteScript(DeleteStoredScriptRequest deleteStoredScriptRequest) {
        String endpoint = new RequestConverters.EndpointBuilder().addPathPartAsIs("_scripts").addPathPart(deleteStoredScriptRequest.id()).build();
        Request request = new Request(HttpDelete.METHOD_NAME, endpoint);
        RequestConverters.Params params = new RequestConverters.Params();
        params.withTimeout(deleteStoredScriptRequest.timeout());
        params.withMasterTimeout(deleteStoredScriptRequest.masterNodeTimeout());
        request.addParameters(params.asMap());
        return request;
    }

    static HttpEntity createEntity(ToXContent toXContent, XContentType xContentType) throws IOException {
        return createEntity(toXContent, xContentType, ToXContent.EMPTY_PARAMS);
    }

    static HttpEntity createEntity(ToXContent toXContent, XContentType xContentType, ToXContent.Params toXContentParams)
            throws IOException {
        BytesRef source = XContentHelper.toXContent(toXContent, xContentType, toXContentParams, false).toBytesRef();
        return new NByteArrayEntity(source.bytes, source.offset, source.length, createContentType(xContentType));
    }

    static String endpoint(String index, String type, String id) {
        return new RequestConverters.EndpointBuilder().addPathPart(index, type, id).build();
    }

    static String endpoint(String index, String type, String id, String endpoint) {
        return new RequestConverters.EndpointBuilder().addPathPart(index, type, id).addPathPartAsIs(endpoint).build();
    }

    static String endpoint(String[] indices) {
        return new RequestConverters.EndpointBuilder().addCommaSeparatedPathParts(indices).build();
    }

    static String endpoint(String[] indices, String endpoint) {
        return new RequestConverters.EndpointBuilder().addCommaSeparatedPathParts(indices).addPathPartAsIs(endpoint).build();
    }

    static String endpoint(String[] indices, String[] types, String endpoint) {
        return new RequestConverters.EndpointBuilder().addCommaSeparatedPathParts(indices).addCommaSeparatedPathParts(types)
                .addPathPartAsIs(endpoint).build();
    }

    static String endpoint(String[] indices, String endpoint, String[] suffixes) {
        return new RequestConverters.EndpointBuilder().addCommaSeparatedPathParts(indices).addPathPartAsIs(endpoint)
                .addCommaSeparatedPathParts(suffixes).build();
    }

    static String endpoint(String[] indices, String endpoint, String type) {
        return new RequestConverters.EndpointBuilder().addCommaSeparatedPathParts(indices).addPathPartAsIs(endpoint).addPathPart(type).build();
    }

    /**
     * Returns a {@link ContentType} from a given {@link XContentType}.
     *
     * @param xContentType the {@link XContentType}
     * @return the {@link ContentType}
     */
    @SuppressForbidden(reason = "Only allowed place to convert a XContentType to a ContentType")
    public static ContentType createContentType(final XContentType xContentType) {
        return ContentType.create(xContentType.mediaTypeWithoutParameters(), (Charset) null);
    }

    /**
     * Utility class to help with common parameter names and patterns. Wraps
     * a {@link Request} and adds the parameters to it directly.
     */
    static class Params {
        private final Map<String,String> parameters = new HashMap<>();

        Params() {
        }

        RequestConverters.Params putParam(String name, String value) {
            if (Strings.hasLength(value)) {
                parameters.put(name,value);
            }
            return this;
        }

        RequestConverters.Params putParam(String key, TimeValue value) {
            if (value != null) {
                return putParam(key, value.getStringRep());
            }
            return this;
        }

        Map<String, String> asMap(){
            return parameters;
        }

        RequestConverters.Params withDocAsUpsert(boolean docAsUpsert) {
            if (docAsUpsert) {
                return putParam("doc_as_upsert", Boolean.TRUE.toString());
            }
            return this;
        }

        RequestConverters.Params withFetchSourceContext(FetchSourceContext fetchSourceContext) {
            if (fetchSourceContext != null) {
                if (fetchSourceContext.fetchSource() == false) {
                    putParam("_source", Boolean.FALSE.toString());
                }
                if (CollectionUtils.isEmpty(fetchSourceContext.includes()) == false) {
                    putParam("_source_includes", String.join(",", fetchSourceContext.includes()));
                }
                if (CollectionUtils.isEmpty(fetchSourceContext.excludes()) == false) {
                    putParam("_source_excludes", String.join(",", fetchSourceContext.excludes()));
                }
            }
            return this;
        }

        RequestConverters.Params withFields(String[] fields) {
            if (CollectionUtils.isEmpty(fields) == false) {
                return putParam("fields", String.join(",", fields));
            }
            return this;
        }

        RequestConverters.Params withMasterTimeout(TimeValue masterTimeout) {
            return putParam("master_timeout", masterTimeout);
        }

        RequestConverters.Params withPipeline(String pipeline) {
            return putParam("pipeline", pipeline);
        }

        RequestConverters.Params withPreference(String preference) {
            return putParam("preference", preference);
        }

        RequestConverters.Params withSearchType(String searchType) {
            return putParam("search_type", searchType);
        }

        RequestConverters.Params withMaxConcurrentShardRequests(int maxConcurrentShardRequests) {
            return putParam("max_concurrent_shard_requests", Integer.toString(maxConcurrentShardRequests));
        }

        RequestConverters.Params withBatchedReduceSize(int batchedReduceSize) {
            return putParam("batched_reduce_size", Integer.toString(batchedReduceSize));
        }

        RequestConverters.Params withRequestCache(boolean requestCache) {
            return putParam("request_cache", Boolean.toString(requestCache));
        }

        RequestConverters.Params withAllowPartialResults(boolean allowPartialSearchResults) {
            return putParam("allow_partial_search_results", Boolean.toString(allowPartialSearchResults));
        }

        RequestConverters.Params withRealtime(boolean realtime) {
            if (realtime == false) {
                return putParam("realtime", Boolean.FALSE.toString());
            }
            return this;
        }

        RequestConverters.Params withRefresh(boolean refresh) {
            if (refresh) {
                return withRefreshPolicy(RefreshPolicy.IMMEDIATE);
            }
            return this;
        }

        /**
         *  @deprecated If creating a new HLRC ReST API call, use {@link RefreshPolicy}
         *  instead of {@link WriteRequest.RefreshPolicy} from the server project
         */
        @Deprecated
        RequestConverters.Params withRefreshPolicy(WriteRequest.RefreshPolicy refreshPolicy) {
            if (refreshPolicy != WriteRequest.RefreshPolicy.NONE) {
                return putParam("refresh", refreshPolicy.getValue());
            }
            return this;
        }

        RequestConverters.Params withRefreshPolicy(RefreshPolicy refreshPolicy) {
            if (refreshPolicy != RefreshPolicy.NONE) {
                return putParam("refresh", refreshPolicy.getValue());
            }
            return this;
        }

        RequestConverters.Params withRequestsPerSecond(float requestsPerSecond) {
            // the default in AbstractBulkByScrollRequest is Float.POSITIVE_INFINITY,
            // but we don't want to add that to the URL parameters, instead we use -1
            if (Float.isFinite(requestsPerSecond)) {
                return putParam(RethrottleRequest.REQUEST_PER_SECOND_PARAMETER, Float.toString(requestsPerSecond));
            } else {
                return putParam(RethrottleRequest.REQUEST_PER_SECOND_PARAMETER, "-1");
            }
        }

        RequestConverters.Params withRetryOnConflict(int retryOnConflict) {
            if (retryOnConflict > 0) {
                return putParam("retry_on_conflict", String.valueOf(retryOnConflict));
            }
            return this;
        }

        RequestConverters.Params withRouting(String routing) {
            return putParam("routing", routing);
        }

        RequestConverters.Params withSlices(int slices) {
            if (slices == 0) {
                // translate to "auto" value in rest request so the receiving end doesn't throw error
                return putParam("slices", AbstractBulkByScrollRequest.AUTO_SLICES_VALUE);
            }
            return putParam("slices", String.valueOf(slices));
        }

        RequestConverters.Params withStoredFields(String[] storedFields) {
            if (CollectionUtils.isEmpty(storedFields) == false) {
                return putParam("stored_fields", String.join(",", storedFields));
            }
            return this;
        }

        RequestConverters.Params withTerminateAfter(int terminateAfter){
            return putParam("terminate_after", String.valueOf(terminateAfter));
        }

        RequestConverters.Params withTimeout(TimeValue timeout) {
            return putParam("timeout", timeout);
        }

        RequestConverters.Params withVersion(long version) {
            if (version != Versions.MATCH_ANY) {
                return putParam("version", Long.toString(version));
            }
            return this;
        }

        RequestConverters.Params withVersionType(VersionType versionType) {
            if (versionType != VersionType.INTERNAL) {
                return putParam("version_type", versionType.name().toLowerCase(Locale.ROOT));
            }
            return this;
        }

        RequestConverters.Params withIfSeqNo(long ifSeqNo) {
            if (ifSeqNo != SequenceNumbers.UNASSIGNED_SEQ_NO) {
                return putParam("if_seq_no", Long.toString(ifSeqNo));
            }
            return this;
        }

        RequestConverters.Params withIfPrimaryTerm(long ifPrimaryTerm) {
            if (ifPrimaryTerm != SequenceNumbers.UNASSIGNED_PRIMARY_TERM) {
                return putParam("if_primary_term", Long.toString(ifPrimaryTerm));
            }
            return this;
        }

        RequestConverters.Params withWaitForActiveShards(ActiveShardCount activeShardCount) {
            return withWaitForActiveShards(activeShardCount, ActiveShardCount.DEFAULT);
        }

        RequestConverters.Params withWaitForActiveShards(ActiveShardCount activeShardCount, ActiveShardCount defaultActiveShardCount) {
            if (activeShardCount != null && activeShardCount != defaultActiveShardCount) {
                return putParam("wait_for_active_shards", activeShardCount.toString().toLowerCase(Locale.ROOT));
            }
            return this;
        }

        RequestConverters.Params withIndicesOptions(IndicesOptions indicesOptions, Version version) {
            if (indicesOptions != null) {
                withIgnoreUnavailable(indicesOptions.ignoreUnavailable());
                putParam("allow_no_indices", Boolean.toString(indicesOptions.allowNoIndices()));
                String expandWildcards;
                if (indicesOptions.expandWildcardsOpen() == false && indicesOptions.expandWildcardsClosed() == false) {
                    expandWildcards = "none";
                } else {
                    StringJoiner joiner = new StringJoiner(StringUtil.COMMA);
                    if (indicesOptions.expandWildcardsOpen()) {
                        joiner.add("open");
                    }
                    if (indicesOptions.expandWildcardsClosed()) {
                        joiner.add("closed");
                    }
                    expandWildcards = joiner.toString();
                }
                putParam("expand_wildcards", expandWildcards);
                // 8.x 版本已弃用
                if (EasyVersion.V_8_0_0.after(version)) {
                    putParam("ignore_throttled", Boolean.toString(indicesOptions.ignoreThrottled()));
                }
            }
            return this;
        }

        RequestConverters.Params withIgnoreUnavailable(boolean ignoreUnavailable) {
            // Always explicitly place the ignore_unavailable value.
            putParam("ignore_unavailable", Boolean.toString(ignoreUnavailable));
            return this;
        }

        RequestConverters.Params withHuman(boolean human) {
            if (human) {
                putParam("human", Boolean.toString(human));
            }
            return this;
        }

        RequestConverters.Params withLocal(boolean local) {
            if (local) {
                putParam("local", Boolean.toString(local));
            }
            return this;
        }

        RequestConverters.Params withIncludeDefaults(boolean includeDefaults) {
            if (includeDefaults) {
                return putParam("include_defaults", Boolean.TRUE.toString());
            }
            return this;
        }

        RequestConverters.Params withPreserveExisting(boolean preserveExisting) {
            if (preserveExisting) {
                return putParam("preserve_existing", Boolean.TRUE.toString());
            }
            return this;
        }

        RequestConverters.Params withDetailed(boolean detailed) {
            if (detailed) {
                return putParam("detailed", Boolean.TRUE.toString());
            }
            return this;
        }

        RequestConverters.Params withWaitForCompletion(Boolean waitForCompletion) {
            return putParam("wait_for_completion", waitForCompletion.toString());
        }

        RequestConverters.Params withNodes(String[] nodes) {
            return withNodes(Arrays.asList(nodes));
        }

        RequestConverters.Params withNodes(List<String> nodes) {
            if (nodes != null && nodes.size() > 0) {
                return putParam("nodes", String.join(",", nodes));
            }
            return this;
        }

        RequestConverters.Params withActions(String[] actions) {
            return withActions(Arrays.asList(actions));
        }

        RequestConverters.Params withActions(List<String> actions) {
            if (actions != null && actions.size() > 0) {
                return putParam("actions", String.join(",", actions));
            }
            return this;
        }

        RequestConverters.Params withTaskId(org.elasticsearch.tasks.TaskId taskId) {
            if (taskId != null && taskId.isSet()) {
                return putParam("task_id", taskId.toString());
            }
            return this;
        }

        RequestConverters.Params withParentTaskId(org.elasticsearch.tasks.TaskId parentTaskId) {
            if (parentTaskId != null && parentTaskId.isSet()) {
                return putParam("parent_task_id", parentTaskId.toString());
            }
            return this;
        }

        RequestConverters.Params withTaskId(TaskId taskId) {
            if (taskId != null && taskId.isSet()) {
                return putParam("task_id", taskId.toString());
            }
            return this;
        }

        RequestConverters.Params withParentTaskId(TaskId parentTaskId) {
            if (parentTaskId != null && parentTaskId.isSet()) {
                return putParam("parent_task_id", parentTaskId.toString());
            }
            return this;
        }

        RequestConverters.Params withWaitForStatus(ClusterHealthStatus status) {
            if (status != null) {
                return putParam("wait_for_status", status.name().toLowerCase(Locale.ROOT));
            }
            return this;
        }

        RequestConverters.Params withWaitForNoRelocatingShards(boolean waitNoRelocatingShards) {
            if (waitNoRelocatingShards) {
                return putParam("wait_for_no_relocating_shards", Boolean.TRUE.toString());
            }
            return this;
        }

        RequestConverters.Params withWaitForNoInitializingShards(boolean waitNoInitShards) {
            if (waitNoInitShards) {
                return putParam("wait_for_no_initializing_shards", Boolean.TRUE.toString());
            }
            return this;
        }

        RequestConverters.Params withWaitForNodes(String waitForNodes) {
            return putParam("wait_for_nodes", waitForNodes);
        }

        RequestConverters.Params withLevel(ClusterHealthRequest.Level level) {
            return putParam("level", level.name().toLowerCase(Locale.ROOT));
        }

        RequestConverters.Params withWaitForEvents(Priority waitForEvents) {
            if (waitForEvents != null) {
                return putParam("wait_for_events", waitForEvents.name().toLowerCase(Locale.ROOT));
            }
            return this;
        }
    }

    /**
     * Ensure that the {@link IndexRequest}'s content type is supported by the Bulk API and that it conforms
     * to the current {@link BulkRequest}'s content type (if it's known at the time of this method get called).
     *
     * @return the {@link IndexRequest}'s content type
     */
    static XContentType enforceSameContentType(IndexRequest indexRequest, @Nullable XContentType xContentType) {
        XContentType requestContentType = indexRequest.getContentType();
        if (requestContentType != XContentType.JSON && requestContentType != XContentType.SMILE) {
            throw new IllegalArgumentException("Unsupported content-type found for request with content-type [" + requestContentType
                    + "], only JSON and SMILE are supported");
        }
        if (xContentType == null) {
            return requestContentType;
        }
        if (requestContentType != xContentType) {
            throw new IllegalArgumentException("Mismatching content-type found for request with content-type [" + requestContentType
                    + "], previous requests have content-type [" + xContentType + "]");
        }
        return xContentType;
    }

    /**
     * Utility class to build request's endpoint given its parts as strings
     */
    static class EndpointBuilder {

        private final StringJoiner joiner = new StringJoiner("/", "/", "");

        RequestConverters.EndpointBuilder addPathPart(String... parts) {
            for (String part : parts) {
                if (Strings.hasLength(part)) {
                    joiner.add(encodePart(part));
                }
            }
            return this;
        }

        RequestConverters.EndpointBuilder addCommaSeparatedPathParts(String[] parts) {
            addPathPart(String.join(",", parts));
            return this;
        }

        RequestConverters.EndpointBuilder addCommaSeparatedPathParts(List<String> parts) {
            addPathPart(String.join(",", parts));
            return this;
        }

        RequestConverters.EndpointBuilder addPathPartAsIs(String ... parts) {
            for (String part : parts) {
                if (Strings.hasLength(part)) {
                    joiner.add(part);
                }
            }
            return this;
        }

        String build() {
            return joiner.toString();
        }

        private static String encodePart(String pathPart) {
            try {
                //encode each part (e.g. index, type and id) separately before merging them into the path
                //we prepend "/" to the path part to make this path absolute, otherwise there can be issues with
                //paths that start with `-` or contain `:`
                //the authority must be an empty string and not null, else paths that being with slashes could have them
                //misinterpreted as part of the authority.
                URI uri = new URI(null, "", "/" + pathPart, null, null);
                //manually encode any slash that each part may contain
                return uri.getRawPath().substring(1).replaceAll("/", "%2F");
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Path part [" + pathPart + "] couldn't be encoded", e);
            }
        }
    }
}