package com.example.watchtest.uii;

/** The three calls the watch makes to the UII server. */
public interface Api {
    ApiResult pair(String code, String name, String model);

    ApiResult postSamples(String token, String json);

    ApiResult postAlert(String token, String json);
}
