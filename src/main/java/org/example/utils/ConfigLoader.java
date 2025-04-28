package org.example.utils;

import io.vertx.config.ConfigRetriever;

import io.vertx.config.ConfigRetrieverOptions;

import io.vertx.config.ConfigStoreOptions;

import io.vertx.core.Vertx;

import io.vertx.core.json.JsonObject;

import io.vertx.core.Future;
import org.example.constants.Constants;

public class ConfigLoader
{

    /**
     * Loads the configuration asynchronously from a file.
     *
     * @param vertx The Vert.x instance.
     * @return A Future that will complete with the loaded configuration as a JsonObject.
     */
    public static Future<JsonObject> load(Vertx vertx)
    {

        var fileStore = new ConfigStoreOptions()
                .setType("file")
                .setFormat("json")
                .setConfig(new JsonObject().put("path", Constants.CONFIG_FILE_PATH));

        var options = new ConfigRetrieverOptions().addStore(fileStore);

        var retriever = ConfigRetriever.create(vertx, options);

        return retriever.getConfig();

    }

}