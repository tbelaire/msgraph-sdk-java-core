package com.microsoft.graph.content;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.core.IBaseClient;
import com.google.gson.JsonParseException;

import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;

/**
 * Represents the content of a batch request
 */
public class MSBatchRequestContent {
    private final LinkedHashMap<String, MSBatchRequestStep> batchRequestStepsHashMap;

    /**
     * Maximum number of requests that can be sent in a batch
     */
    public static final int MAX_NUMBER_OF_REQUESTS = 20;

    /**
     * Creates Batch request content using list provided
     *
     * @param batchRequestStepsArray List of batch steps for batching
     */
    public MSBatchRequestContent(@Nonnull final List<MSBatchRequestStep> batchRequestStepsArray) {
        if (batchRequestStepsArray.size() > MAX_NUMBER_OF_REQUESTS)
            throw new IllegalArgumentException("Number of batch request steps cannot exceed " + MAX_NUMBER_OF_REQUESTS);

        this.batchRequestStepsHashMap = new LinkedHashMap<>();
        for (final MSBatchRequestStep requestStep : batchRequestStepsArray)
            addBatchRequestStep(requestStep);
    }

    /**
     * Creates empty batch request content
     */
    public MSBatchRequestContent() {
        this.batchRequestStepsHashMap = new LinkedHashMap<>();
    }

    /**
     * Adds a step to the current batch
     * @param batchRequestStep Batch request step adding to batch content
     * @return true or false based on addition or no addition of batch request step
     * given
     */
    public boolean addBatchRequestStep(@Nonnull final MSBatchRequestStep batchRequestStep) {
        if (batchRequestStepsHashMap.containsKey(batchRequestStep.getRequestId()) ||
            batchRequestStepsHashMap.size() >= MAX_NUMBER_OF_REQUESTS)
            return false;
        batchRequestStepsHashMap.put(batchRequestStep.getRequestId(), batchRequestStep);
        return true;
    }

    /**
     * Add steps to batch from OkHttp.Request
     * @param request the request to add to the batch
     * @param arrayOfDependsOnIds ids of steps this step depends on
     * @return the step id
     */
    @Nonnull
    public String addBatchRequestStep(@Nonnull final Request request, @Nullable final String... arrayOfDependsOnIds) {
        String requestId;
        do {
            requestId = Integer.toString(ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE));
        } while(batchRequestStepsHashMap.keySet().contains(requestId));
        if(addBatchRequestStep(new MSBatchRequestStep(requestId, request, Arrays.asList(arrayOfDependsOnIds))))
            return requestId;
        else
            throw new IllegalArgumentException("unable to add step to batch. Number of batch request steps cannot exceed " + MAX_NUMBER_OF_REQUESTS);
    }

    /**
     * @param requestId Id of Batch request step to be removed
     *
     * @return true or false based on removal or no removal of batch request step
     * with given id
     */
    public boolean removeBatchRequestStepWithId(@Nonnull final String requestId) {
        boolean removed = false;
        if (batchRequestStepsHashMap.containsKey(requestId)) {
            batchRequestStepsHashMap.remove(requestId);
            removed = true;
            for (final Map.Entry<String, MSBatchRequestStep> steps : batchRequestStepsHashMap.entrySet()) {
                if (steps.getValue() != null && steps.getValue().getArrayOfDependsOnIds() != null) {
                    while (steps.getValue().getArrayOfDependsOnIds().remove(requestId))
                        ;
                }
            }
        }
        return removed;
    }

    /**
     * @return Batch request content's json as String
     */
    @Nonnull
    public String getBatchRequestContent() {
        final JsonObject batchRequestContentMap = new JsonObject();
        final JsonArray batchContentArray = new JsonArray();
        for (final Map.Entry<String, MSBatchRequestStep> requestStep : batchRequestStepsHashMap.entrySet()) {
            batchContentArray.add(getBatchRequestObjectFromRequestStep(requestStep.getValue()));
        }
        batchRequestContentMap.add("requests", batchContentArray);

        final String content = batchRequestContentMap.toString();
        return content;
    }

    /**
     * Executes the batch requests and returns the response
     * @param client client to use for the request
     * @return the batch response
     * @throws ClientException when the batch couldn't be executed because of client issues.
     */
    @Nonnull
    public MSBatchResponseContent execute(@Nonnull final IBaseClient client) throws ClientException {
        try {
            return executeAsync(client).get();
        } catch (Exception ex) {
            throw new ClientException("Batch failed to execute", ex);
        }
    }
    /**
     * Executes the batch requests asynchronously and returns the response
     * @param client client to use for the request
     * @return a future with the batch response
     */
    @Nonnull
    public CompletableFuture<MSBatchResponseContent> executeAsync(@Nonnull final IBaseClient client) {
        if(client == null) {
            throw new IllegalArgumentException("client parameter cannot be null");
        }
        final String content = getBatchRequestContent();
        return client.customRequest(client.getServiceRoot() + "/$batch", String.class)
            .buildRequest()
            .postAsync(content)
            .thenApply(resp -> new MSBatchResponseContent(client.getServiceRoot() + "/", content, resp));
    }

    private static final Pattern protocolAndHostReplacementPattern = Pattern.compile("(?i)^http[s]?:\\/\\/graph\\.microsoft\\.com\\/(?>v1\\.0|beta)\\/?"); // (?i) case insensitive
    private JsonObject getBatchRequestObjectFromRequestStep(final MSBatchRequestStep batchRequestStep) {
        final JsonObject contentmap = new JsonObject();
        contentmap.add("id", new JsonPrimitive(batchRequestStep.getRequestId()));

        final Matcher protocolAndHostReplacementMatcher = protocolAndHostReplacementPattern.matcher(batchRequestStep.getRequest().url().toString());

        final String url =  protocolAndHostReplacementMatcher.replaceAll("");
        contentmap.add("url", new JsonPrimitive(url));

        contentmap.add("method", new JsonPrimitive(batchRequestStep.getRequest().method().toString()));

        final Headers headers = batchRequestStep.getRequest().headers();
        if (headers != null && headers.size() != 0) {
            final JsonObject headerMap = new JsonObject();
            for (final Map.Entry<String, List<String>> entry : headers.toMultimap().entrySet()) {
                headerMap.add(entry.getKey(), new JsonPrimitive(getHeaderValuesAsString(entry.getValue())));
            }
            contentmap.add("headers", headerMap);
        }

        final List<String> arrayOfDependsOnIds = batchRequestStep.getArrayOfDependsOnIds();
        if (arrayOfDependsOnIds != null) {
            final JsonArray array = new JsonArray();
            for (final String dependsOnId : arrayOfDependsOnIds)
                array.add(dependsOnId);
            contentmap.add("dependsOn", array);
        }

        final RequestBody body = batchRequestStep.getRequest().body();
        if (body != null) {
            try {
                contentmap.add("body", requestBodyToJSONObject(batchRequestStep.getRequest()));
            } catch (IOException | JsonParseException e) {
                e.printStackTrace();
            }
        }
        return contentmap;
    }

    private String getHeaderValuesAsString(final List<String> list) {
        if (list == null || list.size() == 0)
            return "";
        final StringBuilder builder = new StringBuilder(list.get(0));
        for (int i = 1; i < list.size(); i++) {
            builder.append(";");
            builder.append(list.get(i));
        }
        return builder.toString();
    }

    private JsonObject requestBodyToJSONObject(final Request request) throws IOException, JsonParseException {
        if (request == null || request.body() == null)
            return null;
        final Request copy = request.newBuilder().build();
        final Buffer buffer = new Buffer();
        copy.body().writeTo(buffer);
        final String requestBody = buffer.readUtf8();
        if(requestBody == null || requestBody == "")
            return null;
        final JsonElement requestBodyElement = JsonParser.parseString(requestBody);
        if(requestBodyElement == null || !requestBodyElement.isJsonObject())
            return null;
        else
            return requestBodyElement.getAsJsonObject();
    }

}
