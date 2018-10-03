package com.gettipsi.stripe.util;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.stripe.android.model.Card;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Created by dmitriy on 11/25/16
 */

public class Utils {

    public static String validateCard(final Card card) {
        if (!card.validateNumber()) {
            return "The card number that you entered is invalid";
        } else if (!card.validateExpiryDate()) {
            return "The expiration date that you entered is invalid";
        } else if (!card.validateCVC()) {
            return "The CVC code that you entered is invalid";
        }
        return null;
    }

  public static WritableMap objectToWritableMap(JSONObject jsonObject) throws JSONException {
    WritableMap writableMap = Arguments.createMap();
    Iterator iterator = jsonObject.keys();
    while(iterator.hasNext()) {
      String key = (String) iterator.next();
      Object value = jsonObject.get(key);
      if (value instanceof Float || value instanceof Double) {
        writableMap.putDouble(key, jsonObject.getDouble(key));
      } else if (value instanceof Number) {
        writableMap.putInt(key, jsonObject.getInt(key));
      } else if (value instanceof String) {
        writableMap.putString(key, jsonObject.getString(key));
      } else if (value instanceof JSONObject) {
        writableMap.putMap(key, objectToWritableMap(jsonObject.getJSONObject(key)));
      } else if (value instanceof JSONArray){
        writableMap.putArray(key, arrayToWritableMap(jsonObject.getJSONArray(key)));
      } else if (value == JSONObject.NULL){
        writableMap.putNull(key);
      }
    }

    return writableMap;
  }

  public static WritableArray arrayToWritableMap(JSONArray jsonArray) throws JSONException {
    WritableArray writableArray = Arguments.createArray();
    for(int i=0; i < jsonArray.length(); i++) {
      Object value = jsonArray.get(i);
      if (value instanceof Float || value instanceof Double) {
        writableArray.pushDouble(jsonArray.getDouble(i));
      } else if (value instanceof Number) {
        writableArray.pushInt(jsonArray.getInt(i));
      } else if (value instanceof String) {
        writableArray.pushString(jsonArray.getString(i));
      } else if (value instanceof JSONObject) {
        writableArray.pushMap(objectToWritableMap(jsonArray.getJSONObject(i)));
      } else if (value instanceof JSONArray){
        writableArray.pushArray(arrayToWritableMap(jsonArray.getJSONArray(i)));
      } else if (value == JSONObject.NULL){
        writableArray.pushNull();
      }
    }
    return writableArray;
  }
}
