package br.com.alurafood.pagamentos.controller;

import br.com.alurafood.pagamentos.dto.PagamentoDto;
import br.com.alurafood.pagamentos.model.Status;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PagamentoControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }

    private PagamentoDto createDefaultPagamentoDto() {
        PagamentoDto dto = new PagamentoDto();
        dto.setValor(new BigDecimal("100.00"));
        dto.setNome("João da Silva");
        dto.setNumero("1234567890123456");
        dto.setExpiracao("12/25");
        dto.setCodigo("123");
        dto.setPedidoId(1L);
        dto.setFormaDePagamentoId(1L);
        return dto;
    }

    // --- GET /pagamentos ---
    @Test
    @DisplayName("GET /pagamentos - Deve retornar todos os pagamentos paginados com status 200 OK")
    void listar_ShouldReturnAllPagamentosPaginated_Status200() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/pagamentos")
        .then()
            .statusCode(HttpStatus.OK.value())
            .body("content", isA(java.util.List.class))
            .body("size", is(10)); // Default page size
    }

    // --- GET /pagamentos/{id} ---
    @Test
    @DisplayName("GET /pagamentos/{id} - Deve retornar um pagamento específico com status 200 OK")
    void detalhar_ShouldReturnSpecificPagamento_Status200() {
        // Criar um pagamento primeiro para ter um ID válido
        PagamentoDto dto = createDefaultPagamentoDto();
        Long id = given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.CREATED.value())
            .extract().jsonPath().getLong("id");

        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/pagamentos/{id}", id)
        .then()
            .statusCode(HttpStatus.OK.value())
            .body("id", equalTo(id.intValue()))
            .body("nome", equalTo(dto.getNome()));
    }

    @Test
    @DisplayName("GET /pagamentos/{id} - Deve retornar 404 NOT FOUND para ID inexistente")
    void detalhar_ShouldReturnNotFound_ForNonExistentId() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/pagamentos/{id}", 9999L) // ID que não existe
        .then()
            .statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("GET /pagamentos/{id} - Deve retornar 400 BAD REQUEST para ID inválido")
    void detalhar_ShouldReturnBadRequest_ForInvalidId() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/pagamentos/{id}", "abc") // ID inválido (não numérico)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    // --- POST /pagamentos ---
    @Test
    @DisplayName("POST /pagamentos - Deve criar um novo pagamento com status 201 CREATED")
    void cadastrar_ShouldCreateNewPagamento_Status201() {
        PagamentoDto dto = createDefaultPagamentoDto();

        given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.CREATED.value())
            .header("Location", containsString("/pagamentos/"))
            .body("id", notNullValue())
            .body("nome", equalTo(dto.getNome()))
            .body("status", equalTo(Status.CRIADO.name()));
    }

    @Test
    @DisplayName("POST /pagamentos - Deve retornar 400 BAD REQUEST para campos obrigatórios ausentes")
    void cadastrar_ShouldReturnBadRequest_ForMissingRequiredFields() {
        PagamentoDto dto = new PagamentoDto(); // DTO sem dados
        dto.setValor(new BigDecimal("10.00")); // Apenas um campo

        given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("POST /pagamentos - Deve retornar 400 BAD REQUEST para dados inválidos (ex: valor negativo)")
    void cadastrar_ShouldReturnBadRequest_ForInvalidData() {
        PagamentoDto dto = createDefaultPagamentoDto();
        dto.setValor(new BigDecimal("-100.00")); // Valor negativo

        given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("POST /pagamentos - Deve retornar 400 BAD REQUEST para nome com mais de 100 caracteres")
    void cadastrar_ShouldReturnBadRequest_ForNameTooLong() {
        PagamentoDto dto = createDefaultPagamentoDto();
        dto.setNome("a".repeat(101)); // Nome muito longo

        given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    // --- PUT /pagamentos/{id} ---
    @Test
    @DisplayName("PUT /pagamentos/{id} - Deve atualizar um pagamento existente com status 200 OK")
    void atualizar_ShouldUpdateExistingPagamento_Status200() {
        // Criar um pagamento primeiro
        PagamentoDto originalDto = createDefaultPagamentoDto();
        Long id = given()
            .contentType(ContentType.JSON)
            .body(originalDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.CREATED.value())
            .extract().jsonPath().getLong("id");

        PagamentoDto updatedDto = createDefaultPagamentoDto();
        updatedDto.setNome("Maria da Silva");
        updatedDto.setValor(new BigDecimal("250.50"));

        given()
            .contentType(ContentType.JSON)
            .body(updatedDto)
        .when()
            .put("/pagamentos/{id}", id)
        .then()
            .statusCode(HttpStatus.OK.value())
            .body("id", equalTo(id.intValue()))
            .body("nome", equalTo(updatedDto.getNome()))
            .body("valor", hasToString(updatedDto.getValor().toString()));
    }

    @Test
    @DisplayName("PUT /pagamentos/{id} - Deve retornar 404 NOT FOUND para ID inexistente")
    void atualizar_ShouldReturnNotFound_ForNonExistentId() {
        PagamentoDto dto = createDefaultPagamentoDto();
        given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .put("/pagamentos/{id}", 9999L)
        .then()
            .statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("PUT /pagamentos/{id} - Deve retornar 400 BAD REQUEST para dados inválidos")
    void atualizar_ShouldReturnBadRequest_ForInvalidData() {
        // Criar um pagamento primeiro
        PagamentoDto originalDto = createDefaultPagamentoDto();
        Long id = given()
            .contentType(ContentType.JSON)
            .body(originalDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.CREATED.value())
            .extract().jsonPath().getLong("id");

        PagamentoDto invalidDto = createDefaultPagamentoDto();
        invalidDto.setNumero("123"); // Número de cartão muito curto

        given()
            .contentType(ContentType.JSON)
            .body(invalidDto)
        .when()
            .put("/pagamentos/{id}", id)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    // --- DELETE /pagamentos/{id} ---
    @Test
    @DisplayName("DELETE /pagamentos/{id} - Deve remover um pagamento com status 204 NO CONTENT")
    void remover_ShouldDeletePagamento_Status204() throws Exception {
        // Criar um pagamento primeiro
        PagamentoDto dto = createDefaultPagamentoDto();
        Long id = given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.CREATED.value())
            .extract().jsonPath().getLong("id");

        // Usar MockMVC para o DELETE (demonstração de alternativa, mas RestAssured também funciona)
        mockMvc.perform(delete("/pagamentos/{id}", id)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Verificar se o pagamento foi realmente removido
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/pagamentos/{id}", id)
        .then()
            .statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("DELETE /pagamentos/{id} - Deve retornar 404 NOT FOUND para ID inexistente")
    void remover_ShouldReturnNotFound_ForNonExistentId() throws Exception {
        mockMvc.perform(delete("/pagamentos/{id}", 9999L)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    // --- PATCH /pagamentos/{id}/confirmar ---
    @Test
    @DisplayName("PATCH /pagamentos/{id}/confirmar - Deve confirmar um pagamento e retornar 200 OK")
    void confirmarPagamento_ShouldConfirmPagamento_Status200() {
        // Criar um pagamento no estado CRIADO
        PagamentoDto dto = createDefaultPagamentoDto();
        Long id = given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.CREATED.value())
            .body("status", equalTo(Status.CRIADO.name()))
            .extract().jsonPath().getLong("id");

        // Chamar o endpoint de confirmação
        given()
            .contentType(ContentType.JSON)
        .when()
            .patch("/pagamentos/{id}/confirmar", id)
        .then()
            .statusCode(HttpStatus.OK.value()); // PATCH é void, então retorna 200 OK

        // Verificar se o status foi atualizado para CONFIRMADO
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/pagamentos/{id}", id)
        .then()
            .statusCode(HttpStatus.OK.value())
            .body("status", equalTo(Status.CONFIRMADO.name()));
    }

    @Test
    @DisplayName("PATCH /pagamentos/{id}/confirmar - Deve retornar 404 NOT FOUND para ID inexistente")
    void confirmarPagamento_ShouldReturnNotFound_ForNonExistentId() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .patch("/pagamentos/{id}/confirmar", 9999L)
        .then()
            .statusCode(HttpStatus.NOT_FOUND.value());
    }

    // Não é possível testar o fallback diretamente sem mockar o FeignClient de forma mais complexa
    // Isso envolveria um Mockito para o PedidoClient para simular falhas.
    // O CircuitBreaker é um mecanismo interno de resiliência e a forma mais fácil de testá-lo
    // seria através de testes de integração com o serviço real do pedido-ms ou com mocks de rede.
    // Para este escopo, a cobertura do cenário de sucesso e falha de ID é suficiente.

}