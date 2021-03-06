package com.microsoft.graph.httpcore;

import java.io.IOException;
import java.lang.reflect.Field;

import com.microsoft.graph.httpcore.middlewareoption.TelemetryOptions;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class TelemetryHandler implements Interceptor{

    public static final String SDK_VERSION = "SdkVersion";
    public static final String VERSION = "v1.0.6";
    public static final String GRAPH_VERSION_PREFIX = "graph-java-core";
    public static final String JAVA_VERSION_PREFIX = "java";
    public static final String ANDROID_VERSION_PREFIX = "android";
    public static final String CLIENT_REQUEST_ID = "client-request-id";

    @Override
    public Response intercept(Chain chain) throws IOException {
        final Request request = chain.request();
        final Request.Builder telemetryAddedBuilder = request.newBuilder();

        TelemetryOptions telemetryOptions = request.tag(TelemetryOptions.class);
        if(telemetryOptions == null)
            telemetryOptions = new TelemetryOptions();

        final String featureUsage = "(featureUsage=" + telemetryOptions.getFeatureUsage() + ")";
        final String javaVersion = System.getProperty("java.version");
        final String androidVersion = getAndroidAPILevel();
        final String sdkversion_value = GRAPH_VERSION_PREFIX + "/" + VERSION + " " + featureUsage + " " + JAVA_VERSION_PREFIX + "/" + javaVersion + " " + ANDROID_VERSION_PREFIX + "/" + androidVersion;
        telemetryAddedBuilder.addHeader(SDK_VERSION, sdkversion_value);

        if(request.header(CLIENT_REQUEST_ID) == null) {
            telemetryAddedBuilder.addHeader(CLIENT_REQUEST_ID, telemetryOptions.getClientRequestId());
        }

        return chain.proceed(telemetryAddedBuilder.build());
    }

    private String androidAPILevel;
    private String getAndroidAPILevel() {
        if(androidAPILevel == null) {
            androidAPILevel = getAndroidAPILevelInternal();
        }
        return androidAPILevel;
    }
    private String getAndroidAPILevelInternal() {
        try {
            final Class<?> buildClass = Class.forName("android.os.Build");
            final Class<?>[] subclasses = buildClass.getDeclaredClasses();
            Class<?> versionClass = null;
            for(final Class<?> subclass : subclasses) {
                if(subclass.getName().endsWith("VERSION")) {
                    versionClass = subclass;
                    break;
                }
            }
            final Field sdkVersionField = versionClass.getField("SDK_INT");
            final Object value = sdkVersionField.get(null);
            final String valueStr = String.valueOf(value);
            return valueStr == null || valueStr == "" ? "0" : valueStr;
        } catch (IllegalAccessException | ClassNotFoundException | NoSuchFieldException ex) {
            // we're not on android and return "0" to align with java version which returns "0" when running on android
            return "0";
        }
    }
}
