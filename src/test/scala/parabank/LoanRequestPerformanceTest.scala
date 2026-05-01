package parabank

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * LoanRequestPerformanceTest - Historia de Usuario No Funcional 4: Solicitud de préstamo bajo carga
 * 
 * Como usuario del banco, quiero que el sistema procese solicitudes de préstamo de forma rápida y sin errores, 
 * incluso cuando muchos usuarios las envían al mismo tiempo, para que pueda obtener una respuesta oportuna sobre mi elegibilidad.
 * 
 * Criterios de aceptación:
 * - Con una carga de 150 usuarios concurrentes realizando solicitudes de préstamo
 * - El tiempo de respuesta promedio debe ser ≤ 5 segundos
 * - El sistema debe mantener una tasa de éxito ≥ 98%
 * - No deben presentarse errores de validación inesperados ni caídas del servicio
 * 
 * Método de inyección: incrementUsersPerSec (carga incremental)
 */
class LoanRequestPerformanceTest extends Simulation {

  // Configuración HTTP optimizada
  val httpConf = http
    .baseUrl("https://parabank.parasoft.com/parabank")
    .acceptHeader("application/json, text/html, */*")
    .userAgentHeader("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
    .contentTypeHeader("application/x-www-form-urlencoded")
    .connectionHeader("keep-alive")
    .maxConnectionsPerHost(20)
    .shareConnections

  // Feeder CSV — provee customerId, amount, downPayment
  val loanFeeder = csv("data/loans.csv").circular

  // Escenario de solicitud de préstamo
  val loanRequestScenario = scenario("Loan Request Performance Test")
    .feed(loanFeeder)
    .exec(
      // Paso 1: Login con credenciales válidas de ParaBank
      http("Login for Loan")
        .post("/login.htm")
        .formParam("username", "john")
        .formParam("password", "demo")
        .check(status.in(200, 302))
    )
    .pause(1)
    .exec(
      // Paso 2: Obtener cuentas reales del cliente para usar un fromAccountId válido
      http("Get Customer Accounts")
        .get("/services_proxy/bank/customers/${customerId}/accounts")
        .check(status.is(200))
        .check(jsonPath("$[0].id").saveAs("realFromAccountId"))
    )
    .exec(
      // Paso 3: Navegar a solicitud de préstamo
      http("Navigate to Loan Request")
        .get("/requestloan.htm")
        .check(status.in(200, 302))
    )
    .pause(1)
    .exec(
      // Paso 4: Enviar solicitud de préstamo con cuenta real
      http("Submit Loan Request")
        .post("/requestloan.htm")
        .formParam("customerId", "${customerId}")
        .formParam("fromAccountId", "${realFromAccountId}")
        .formParam("amount", "${amount}")
        .formParam("downPayment", "${downPayment}")
        .check(status.in(200, 302))
    )

  // Configuración de la simulación con incrementUsersPerSec
  setUp(
    loanRequestScenario
      .inject(
        // Carga incremental: empezar con pocos usuarios y aumentar gradualmente
        incrementUsersPerSec(5) // Empezar con 5 usuarios/seg
          .times(10) // Incrementar 10 veces
          .eachLevelLasting(30.seconds) // Cada nivel dura 30 segundos
          .separatedByRampsLasting(10.seconds) // Separado por rampas de 10 segundos
          .startingFrom(5), // Empezar desde 5 usuarios/seg
        
        // Mantener carga pico por un tiempo
        constantUsersPerSec(50).during(60.seconds), // 50 usuarios/seg = ~150 concurrentes
        
        // Descenso gradual
        incrementUsersPerSec(0)
          .times(10)
          .eachLevelLasting(30.seconds)
          .separatedByRampsLasting(10.seconds)
          .startingFrom(50)
      )
  )
    .protocols(httpConf)
    .assertions(
      global.responseTime.mean.lte(5000),        // 150 usuarios concurrentes: promedio ≤ 5 segundos
      global.successfulRequests.percent.gte(98)  // Tasa de éxito ≥ 98%
    )
}
