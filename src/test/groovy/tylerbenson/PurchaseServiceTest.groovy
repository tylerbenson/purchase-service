package tylerbenson

import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.http.client.ReceivedResponse
import ratpack.impose.ForceDevelopmentImposition
import ratpack.impose.ImpositionsSpec
import ratpack.impose.ServerConfigImposition
import ratpack.test.ApplicationUnderTest
import ratpack.test.MainClassApplicationUnderTest
import ratpack.test.http.TestHttpClient
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.Charset

import static java.util.Collections.singletonMap

class PurchaseServiceTest extends Specification {
  @AutoCleanup
  @Shared
  ApplicationUnderTest aut = new MainClassApplicationUnderTest(PurchaseService.class) {
    @Override
    protected void addImpositions(ImpositionsSpec impositions) {
      def backend = backendService.address.host + ":" + backendService.address.port
      impositions.add(ServerConfigImposition.of {
        it.props(singletonMap("backend", backend))
      })
      impositions.add(ForceDevelopmentImposition.of(true))
    }
  }

  @Delegate
  TestHttpClient client = TestHttpClient.testHttpClient(aut)

  @AutoCleanup
  @Shared
  GroovyEmbeddedApp backendService = GroovyEmbeddedApp.of {
    handlers {
      get("api/purchases/by_user/:user_id") {
        response.contentType("application/json")
        if (pathTokens.get("user_id") == "invalid-user")
          render "{\"purchases\":[]}"
        else
          render "{\"purchases\":[{\"id\":562508,\"username\":\"Howard.Jast\"," +
              "\"productId\":490085,\"date\":\"2016-11-11T13:20:34.155Z\"}]}"
      }
      get("api/products/:product_id") {
        response.contentType("application/json")
        render "{\"product\":{\"id\":490085,\"face\":\"ζ༼Ɵ͆ل͜Ɵ͆༽ᶘ\",\"price\":673,\"size\":28}}"
      }
      get("api/purchases/by_product/:product_id") {
        response.contentType("application/json")
        render "{\"purchases\":[{\"id\":562508,\"username\":\"Howard.Jast\"," +
            "\"productId\":490085,\"date\":\"2016-11-11T13:20:34.155Z\"}," +
            "{\"id\":523872,\"username\":\"Liliana.Will\",\"productId\":490085," +
            "\"date\":\"2016-11-10T03:45:16.155Z\"},{\"id\":552107,\"username\":" +
            "\"Hershel.Anderson90\",\"productId\":490085,\"date\":\"2016-11-13T13:10:25.156Z\"}," +
            "{\"id\":276279,\"username\":\"Dakota84\",\"productId\":490085,\"date\":\"2016-11-07T02:47:15.160Z\"}]}"
      }
    }
  }

  def "makes request and returns the response"() {
    setup:
    def ReceivedResponse response = client.get("/api/recent_purchases/${username}")
    def responseText = response.getBody().getText(Charset.defaultCharset())

    expect:
    responseText == "[{\"id\":490085,\"face\":\"ζ༼Ɵ͆ل͜Ɵ͆༽ᶘ\",\"price\":673,\"size\":28," +
        "\"recent\":[\"Howard.Jast\",\"Liliana.Will\",\"Hershel.Anderson90\",\"Dakota84\"]}]"
    response.statusCode == 200

    where:
    username << ["Howard.Jast"]
  }

  def "missing user returns correct message"() {
    setup:
    def ReceivedResponse response = client.get("/api/recent_purchases/${username}")
    def responseText = response.getBody().getText(Charset.defaultCharset())

    expect:
    responseText == "User with username of 'invalid-user' was not found"
    response.statusCode == 404

    where:
    username << ["invalid-user"]
  }

  //TODO Obviously further testing is needed.  This is just covering the base cases.
}
