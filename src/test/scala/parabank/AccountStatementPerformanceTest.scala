package parabank

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * AccountStatementPerformanceTest - Historia de Usuario No Funcional 3: Carga simultánea de estados de cuenta
 * 
 * Como usuario del banco, quiero que la consulta de mi estado de cuenta sea rápida incluso cuando muchos usuarios 
 * la están solicitando al mismo tiempo, para que pueda acceder a mi historial sin retrasos.
 * 
 * Criterios de aceptación:
 * - Consultas a los estados de cuenta deben tener un tiempo de respuesta ≤ 3 segundos con 200 usuarios simultáneos
 * - La tasa de error durante la prueba de carga no debe superar el 1%
 * 
 * Método de inyección: heavisideUsers (distribución realista de carga)
 */
class AccountStatementPerformanceTest extends Simulation {

  // Configuración HTTP optimizada
  val httpConf = http
    .baseUrl("https://parabank.parasoft.com/parabank")
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .userAgentHeader("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .connectionHeader("keep-alive")
    .maxConnectionsPerHost(25)
    .shareConnections

  // Feeders CSV
  val userFeeder = csv("data/users.csv").circular
  val accountFeeder = csv("data/accounts.csv").circular

  // Escenario de consulta de estado de cuenta
  val accountStatementScenario = scenario("Account Statement Performance Test")
    .feed(userFeeder)
    .feed(accountFeeder)
    .exec(
      // Paso 1: Login
      http("Login for Statement")
        .post("/login.htm")
        .formParam("username", "${username}")
        .formParam("password", "${password}")
        .check(status.in(200, 302))
    )
    .pause(1)
    .exec(
      // Paso 2: Consultar estado de cuenta
      http("Get Account Statement")
        .get("/activity.htm")
        .queryParam("id", "${accountId}")
        .check(status.in(200, 302))
    )
    .pause(2)
    .exec(
      // Paso 3: Consultar actividad reciente filtrada
      http("Get Recent Activity")
        .get("/activity.htm")
        .queryParam("id", "${accountId}")
        .queryParam("month", "All")
        .queryParam("transactionType", "All")
        .check(status.in(200, 302))
    )

  // Configuración de la simulación con heavisideUsers
  setUp(
    accountStatementScenario
      .inject(
        // Distribución heaviside: carga realista que simula patrones de uso real
        heavisideUsers(200).during(300.seconds) // 200 usuarios distribuidos en 5 minutos
      )
  )
    .protocols(httpConf)
    .assertions(
      global.responseTime.percentile(95).lte(3000), // 200 usuarios simultáneos: p95 ≤ 3 segundos
      global.failedRequests.percent.lte(1)          // Tasa de error ≤ 1%
    )
}
