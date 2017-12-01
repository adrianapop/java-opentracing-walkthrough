package com.otsample.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.otsample.api.resources.Donut;
import com.otsample.api.resources.DonutAddRequest;
import com.otsample.api.resources.Status;
import com.otsample.api.resources.StatusRes;
import io.opentracing.Span;
import io.opentracing.contrib.okhttp3.SpanDecorator;
import io.opentracing.contrib.okhttp3.TagWrapper;
import io.opentracing.contrib.okhttp3.TracingInterceptor;
import io.opentracing.util.GlobalTracer;
import okhttp3.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class KitchenConsumer {
    OkHttpClient client;
    MediaType jsonType;

    public KitchenConsumer() {
        TracingInterceptor tracingInterceptor = new TracingInterceptor(
                GlobalTracer.get(),
                Arrays.asList(SpanDecorator.STANDARD_TAGS));
        client = new OkHttpClient.Builder()
                .addInterceptor(tracingInterceptor)
                .addNetworkInterceptor(tracingInterceptor)
                .build();
        jsonType = MediaType.parse("application/json");
    }

    public boolean addDonut(HttpServletRequest request, String orderId) {
        DonutAddRequest donutReq = new DonutAddRequest(orderId);
        RequestBody body = RequestBody.create(jsonType, Utils.toJSON(donutReq));
        System.err.println("############# Sending donut requests to the kitchen service ");
        Span parentSpan = (Span) request.getAttribute("span");
        Request req = new Request.Builder()
                .url("http://127.0.0.1:10001/kitchen/add_donut")
                .post(body)
                .tag(new TagWrapper(parentSpan.context()))
                .build();


        Response res = null;
        try {
            res = client.newCall(req).execute();
        } catch (IOException exc) {
            return false;
        } finally {
        }

        return res.code() >= 200 && res.code() < 300;
    }

    public Collection<Donut> getDonuts(HttpServletRequest request) {
        Span parentSpan = (Span) request.getAttribute("span");
        Request req = new Request.Builder()
                .url("http://127.0.0.1:10001/kitchen/check_donuts")
                .tag(new TagWrapper(parentSpan.context()))
                .build();
        String body = null;
        try {
            Response res = client.newCall(req).execute();
            if (res.code() < 200 || res.code() >= 300)
                return null;

            body = res.body().string();
        } catch (IOException exc) {
            return null;
        }

        Gson gson = new Gson();
        Type collType = new TypeToken<Collection<Donut>>() {
        }.getType();
        return gson.fromJson(body, collType);
    }

    public StatusRes checkStatus(HttpServletRequest request, String orderId) {
        Collection<Donut> donuts = getDonuts(request);
        if (donuts == null)
            return null;

        ArrayList<Donut> filtered = new ArrayList<Donut>();

        for (Donut donut : donuts)
            if (donut.getOrderId().equals(orderId))
                filtered.add(donut);

        Status status = Status.READY;
        int estimatedTime = 0;

        for (Donut donut : filtered) {
            switch (donut.getStatus()) {
                case NEW_ORDER:
                    estimatedTime += 3;
                    status = Status.NEW_ORDER;
                    break;
                case RECEIVED:
                    estimatedTime += 2;
                    status = Status.RECEIVED;
                    break;
                case COOKING:
                    estimatedTime += 1;
                    status = Status.COOKING;
                    break;
            }
        }

        return new StatusRes(orderId, estimatedTime, status);
    }
}
