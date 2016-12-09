package tylerbenson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import ratpack.exec.Promise;
import ratpack.exec.util.ParallelBatch;
import ratpack.func.Function;
import ratpack.func.Pair;
import ratpack.handling.Context;
import ratpack.http.client.HttpClient;
import ratpack.jackson.Jackson;
import ratpack.server.RatpackServer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;
import static ratpack.jackson.Jackson.jsonNode;

public class PurchaseService {
  // Main entry point to the application. Initializes server and returns.
  public static void main(String... args) throws Exception {
    final Cache<String, Promise<Pair<Integer, Object>>> recentPurchasesCache = CacheBuilder.newBuilder()
        .maximumSize(1_000)
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build();

    RatpackServer.start(server -> server
        .serverConfig(config -> config.require("", Config.class))
        .handlers(chain -> chain
            .get("api/recent_purchases/:username", ctx -> {
              final String username = ctx.getPathTokens().get("username");
              Promise<Pair<Integer, Object>> responsePromise = recentPurchasesCache.get(username, () ->
                  loadRecentPurchases(ctx, username).cache());
              responsePromise.then(response -> {
                ctx.getResponse().status(response.getLeft());
                ctx.render(response.getRight()); // Response can either be a string or json.
              });
            })
        )
    );
  }

  private static Promise<Pair<Integer, Object>> loadRecentPurchases(Context ctx, String username) throws URISyntaxException {
    URI userPurchasesUri = new URI("http://" + ctx.get(Config.class).backend + "/api/purchases/by_user/" + username + "?limit=5");
    return ctx.get(HttpClient.class).get(userPurchasesUri)
        .flatMap(resp -> {
          switch (resp.getStatusCode()) {
            case 200:
              try {
                JsonNode json = ctx.parse(resp.getBody(), jsonNode());
                List<Promise<Pair<JsonNode, List<String>>>> promises = json.get("purchases").findValues("productId").stream()
                    .map(JsonNode::asLong)
                    .map(productId -> getProductDetails(ctx, productId).fork().right(getProductHistory(ctx, productId).fork()))
                    .collect(toList());
                if (promises.isEmpty())
                  // Unfortunately the service returns an empty list rather than 404 for invalid usernames
                  return Promise.value(Pair.of(404, "User with username of '" + username + "' was not found"));
                return ParallelBatch.of(promises).yield()
                    .map(productInfos -> productInfos.stream()
                        .map(productInfo -> Pair.of(
                            ctx.get(ObjectMapper.class).convertValue(productInfo.getLeft(), Map.class), productInfo.getRight()))
                        .map(mapListPair -> {
                          mapListPair.getLeft().put("recent", mapListPair.getRight());
                          return Pair.of(mapListPair.getRight().size(), mapListPair.getLeft());
                        })
                        .sorted((o1, o2) -> o2.getLeft().compareTo(o1.getLeft()))
                        .map(Pair::getRight)
                        .collect(toList()))
                    .map(list -> Pair.of(200, Jackson.json(list)));
              } catch (Exception e) {
                e.printStackTrace();
                return Promise.value(Pair.of(500, "Server Error"));
              }
            case 404:
              return Promise.value(Pair.of(404, "User with username of '" + username + "' was not found"));
            default:
              return Promise.value(Pair.of(500, "Unexpected response from remote service"));
          }
        });
  }

  private static Promise<List<String>> getProductHistory(Context ctx, Long productId) {
    return request(ctx, "http://" + ctx.get(Config.class).backend + "/api/purchases/by_product/" + productId,
        (json) -> json.get("purchases").findValues("username").stream()
            .map(JsonNode::asText).collect(toList()));
  }

  private static Promise<JsonNode> getProductDetails(Context ctx, Long productId) {
    return request(ctx, "http://" + ctx.get(Config.class).backend + "/api/products/" + productId,
        (json) -> json.get("product"));
  }

  private static <R> Promise<R> request(Context ctx, String url, Function<JsonNode, R> jsonHandler) {
    try {
      URI uri = new URI(url);
      return ctx.get(HttpClient.class).get(uri)
          .map(resp -> {
            switch (resp.getStatusCode()) {
              case 200:
                JsonNode json = ctx.parse(resp.getBody(), jsonNode());
                return jsonHandler.apply(json);
              default:
                throw new Exception("unexpected response code: " + resp.getStatusCode());
            }
          });
    } catch (URISyntaxException e) {
      return Promise.error(e);
    }
  }

  private static class Config {
    String backend = "74.50.59.155:6000";

    public void setBackend(String backend) {
      this.backend = backend;
    }
  }
}
