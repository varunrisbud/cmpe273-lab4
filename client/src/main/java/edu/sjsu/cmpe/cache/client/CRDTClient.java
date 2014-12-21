package edu.sjsu.cmpe.cache.client;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CRDTClient {

    private ArrayList<DistributedCacheService> cacheServerList;
    private CountDownLatch countDownLatch;

    public CRDTClient() {

        this.cacheServerList = new ArrayList<DistributedCacheService>();

        cacheServerList.add(new DistributedCacheService("http://localhost:3000"));
        cacheServerList.add(new DistributedCacheService("http://localhost:3001"));
        cacheServerList.add(new DistributedCacheService("http://localhost:3002"));
    }

    public boolean put(long key, String value) throws InterruptedException, IOException {
        final AtomicInteger successCount = new AtomicInteger(0);
        this.countDownLatch = new CountDownLatch(cacheServerList.size());
        final ArrayList<DistributedCacheService> writtenServerList = new ArrayList<DistributedCacheService>(3);
        for (final DistributedCacheService cacheServer : cacheServerList) {
            Future<HttpResponse<JsonNode>> future = Unirest.put(cacheServer.getCacheServerUrl() + "/cache/{key}/{value}")
                    .header("accept", "application/json")
                    .routeParam("key", Long.toString(key))
                    .routeParam("value", value)
                    .asJsonAsync(new Callback<JsonNode>() {

                        public void failed(UnirestException e) {
                            System.out.println("The request has failed "+cacheServer.getCacheServerUrl());
                            countDownLatch.countDown();
                        }

                        public void completed(HttpResponse<JsonNode> response) {
                            int count = successCount.incrementAndGet();
                            writtenServerList.add(cacheServer);
                            System.out.println("The request was successful "+cacheServer.getCacheServerUrl());
                            countDownLatch.countDown();
                        }

                        public void cancelled() {
                            System.out.println("The request was unsuccessful");
                            countDownLatch.countDown();
                        }

                    });
        }
        this.countDownLatch.await();
        if (successCount.intValue() >= 1) {
            return true;
        } else {
            System.out.println("Deleting");
            this.countDownLatch = new CountDownLatch(writtenServerList.size());
            for (final DistributedCacheService cacheServer : writtenServerList) {
                cacheServer.remove(key);
                System.out.println("Deleted resource with key: "+key+" from : "+cacheServer.getCacheServerUrl());
            }
            this.countDownLatch.await(3, TimeUnit.SECONDS);
            Unirest.shutdown();
            return false;
        }
    }

    public String get(long key) throws InterruptedException, UnirestException, IOException {
        this.countDownLatch = new CountDownLatch(cacheServerList.size());
        final Map<DistributedCacheService, String> resultMap = new HashMap<DistributedCacheService, String>();
        for (final DistributedCacheService cacheServer : cacheServerList) {
            Future<HttpResponse<JsonNode>> future = Unirest.get(cacheServer.getCacheServerUrl() + "/cache/{key}")
                    .header("accept", "application/json")
                    .routeParam("key", Long.toString(key))
                    .asJsonAsync(new Callback<JsonNode>() {

                        public void failed(UnirestException e) {
                            System.out.println("The request has failed");
                            countDownLatch.countDown();
                        }

                        public void completed(HttpResponse<JsonNode> response) {
                            resultMap.put(cacheServer, response.getBody().getObject().getString("value"));
                            System.out.println("The request was successful "+cacheServer.getCacheServerUrl());
                            countDownLatch.countDown();
                        }

                        public void cancelled() {
                            System.out.println("The request was unsuccessful");
                            countDownLatch.countDown();
                        }
                    });
        }
        this.countDownLatch.await(3, TimeUnit.SECONDS);
        final Map<String, Integer> countMap = new HashMap<String, Integer>();
        int maximumCount = 0;
        for (String value : resultMap.values()) {
            int count = 1;
            if (countMap.containsKey(value)) {
                count = countMap.get(value);
                count++;
            }
            if (maximumCount < count)
                maximumCount = count;
            countMap.put(value, count);
        }
        String value = this.getKeyFromValue(countMap, maximumCount);
        if (maximumCount != this.cacheServerList.size()) {
            for (Map.Entry<DistributedCacheService, String> cacheServerData : resultMap.entrySet()) {
                if (!value.equals(cacheServerData.getValue())) {
                    System.out.println("Repairing "+cacheServerData.getKey());
                    HttpResponse<JsonNode> response = Unirest.put(cacheServerData.getKey() + "/cache/{key}/{value}")
                            .header("accept", "application/json")
                            .routeParam("key", Long.toString(key))
                            .routeParam("value", value)
                            .asJson();
                }
            }
            for (DistributedCacheService cacheServer : this.cacheServerList) {
                if (resultMap.containsKey(cacheServer)) continue;
                System.out.println("Repairing "+cacheServer.getCacheServerUrl());
                HttpResponse<JsonNode> response = Unirest.put(cacheServer.getCacheServerUrl() + "/cache/{key}/{value}")
                        .header("accept", "application/json")
                        .routeParam("key", Long.toString(key))
                        .routeParam("value", value)
                        .asJson();
            }
        } else {
            System.out.println("Repair not required");
        }
        System.out.println("Exiting unirest services");
        Unirest.shutdown();
        return value;
    }

    public String getKeyFromValue(Map<String, Integer> map, int value) {
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (value == entry.getValue()) return entry.getKey();
        }
        return null;
    }

}