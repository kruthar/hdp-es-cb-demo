package com.avalonconsult;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import com.google.code.geocoder.Geocoder;
import com.google.code.geocoder.GeocoderRequestBuilder;
import com.google.code.geocoder.model.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Created by adam on 2/24/15.
 */
public class GeoEnrichmentBolt extends BaseRichBolt {

    private OutputCollector collector;
    private JSONParser parser;
    private Geocoder geocoder;
    private GeocoderRequest request;
    private GeocodeResponse response;

    private static final Logger LOG = LoggerFactory.getLogger(GeoEnrichmentBolt.class);

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;
        this.parser = new JSONParser();
        this.geocoder = new Geocoder();
    }

    @Override
    public void execute(Tuple tuple) {
        JSONObject jsonObject = null;
        try {
            jsonObject = (JSONObject) parser.parse(tuple.getString(0));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        String id = Long.toString((Long) jsonObject.get("id"));
        String user = (String) jsonObject.get("user");
        String name = (String) jsonObject.get("name");
        String text = (String) jsonObject.get("text");
        String location = (String) jsonObject.get("location");
        Long datetime = (Long) jsonObject.get("datetime");

        Object latObject = jsonObject.get("latitude");
        Object lonObject = jsonObject.get("longitude");

        JSONObject tweet = new JSONObject();
        tweet.put("id", id);
        tweet.put("user", user);
        tweet.put("name", name);
        tweet.put("text", text);
        tweet.put("datetime", datetime);

        if (null != latObject && null != lonObject) {
            double latitude = (Double) latObject;
            double longitude = (Double) lonObject;

            tweet.put("latitude", latitude);
            tweet.put("longitude", longitude);
            LOG.info("tweet had coords, lat: " + latObject + ", lon: " + lonObject);
        } else if (null != location && !location.equals("")) {
            this.request = new GeocoderRequestBuilder().setAddress(location).getGeocoderRequest();
            try {
                this.response = this.geocoder.geocode(this.request);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (this.response.getStatus() == GeocoderStatus.OK & !this.response.getResults().isEmpty()) {
                GeocoderResult geocoderResult = this.response.getResults().iterator().next();
                LatLng latitudeLongitude = geocoderResult.getGeometry().getLocation();
                Float[] coords = new Float[2];
                coords[0] = latitudeLongitude.getLat().floatValue();
                coords[1] = latitudeLongitude.getLng().floatValue();

                tweet.put("latitude", coords[0]);
                tweet.put("longitude", coords[1]);
            } else {
                tweet.put("latitude", null);
                tweet.put("longitude", null);
            }
        } else {
            tweet.put("latitude", null);
            tweet.put("longitude", null);
        }

        String tweetString = tweet.toJSONString();
        collector.emit(new Values(tweetString));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        outputFieldsDeclarer.declare(new Fields("jsonString"));
    }
}
