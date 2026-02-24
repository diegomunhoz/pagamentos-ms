package br.com.alurafood.pagamentos.controller;

import br.com.alurafood.pagamentos.dto.PagamentoDto;
import br.com.alurafood.pagamentos.model.Pagamento;
import br.com.alurafood.pagamentos.model.Status;
import br.com.alurafood.pagamentos.repository.PagamentoRepositoy;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") // Garante perfil de teste para não interagir com DB real
class PagamentoControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PagamentoRepositoy pagamentoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private PagamentoDto validPagamentoDto;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        pagamentoRepository.deleteAll(); // Limpa o banco de dados antes de cada teste
        setupValidPagamentoDto();
    }

    private void setupValidPagamentoDto() {
        validPagamentoDto = new PagamentoDto();
        validPagamentoDto.setValor(new BigDecimal("100.00"));
        validPagamentoDto.setNome("João da Silva");
        validPagamentoDto.setNumero("1234567890123456789");
        validPagamentoDto.setExpiracao("12/25");
        validPagamentoDto.setCodigo("123");
        validPagamentoDto.setFormaDePagamentoId(1L);
        validPagamentoDto.setPedidoId(1L);
    }

    // --- GET /pagamentos ---
    @Test
    @DisplayName("GET /pagamentos - Deve retornar todos os pagamentos paginados com sucesso")
    void listarTodosPagamentos_deveRetornarPagamentosComSucesso() {
        // Cenário: Adiciona alguns pagamentos para testar paginação
        Pagamento p1 = new Pagamento(null, new BigDecimal("50.00"), "Cliente A", "1111222233334444", "10/24", "111", Status.CRIADO, 1L, 2L);
        Pagamento p2 = new Pagamento(null, new BigDecimal("75.00"), "Cliente B", "5555666677778888", "11/24", "222", Status.CRIADO, 2L, 3L);
        pagamentoRepository.save(p1);
        pagamentoRepository.save(p2);

        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/pagamentos")
        .then()
            .statusCode(HttpStatus.OK.value())
            .body("content", hasSize(greaterThanOrEqualTo(2)))
            .body("content[0].valor", equalTo(50.0F)) // Float porque JSON/RestAssured converte BigDecimal
            .body("content[1].nome", equalTo("Cliente B"));
    }

    @Test
    @DisplayName("GET /pagamentos - Deve retornar lista vazia quando não há pagamentos")
    void listarTodosPagamentos_deveRetornarVazioQuandoNaoHaPagamentos() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/pagamentos")
        .then()
            .statusCode(HttpStatus.OK.value())
            .body("content", hasSize(0))
            .body("totalElements", equalTo(0));
    }

    // --- GET /pagamentos/{id} ---
    @Test
    @DisplayName("GET /pagamentos/{id} - Deve retornar um pagamento por ID com sucesso")
    void detalharPagamentoPorId_deveRetornarPagamentoComSucesso() {
        Pagamento pagamentoSalvo = pagamentoRepository.save(new Pagamento(null, new BigDecimal("200.00"), "Cliente Teste", "0000111122223333", "01/25", "456", Status.CRIADO, 1L, 1L));

        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/pagamentos/{id}", pagamentoSalvo.getId())
        .then()
            .statusCode(HttpStatus.OK.value())
            .body("id", equalTo(pagamentoSalvo.getId().intValue()))
            .body("valor", equalTo(200.0F))
            .body("nome", equalTo("Cliente Teste"));
    }

    @Test
    @DisplayName("GET /pagamentos/{id} - Deve retornar 404 Not Found para ID inexistente")
    void detalharPagamentoPorId_deveRetornarNotFoundParaIdInexistente() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/pagamentos/{id}", 9999L)
        .then()
            .statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("GET /pagamentos/{id} - Deve retornar 400 Bad Request para ID inválido (não numérico)")
    void detalharPagamentoPorId_deveRetornarBadRequestParaIdInvalido() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/pagamentos/{id}", "abc")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    // --- POST /pagamentos ---
    @Test
    @DisplayName("POST /pagamentos - Deve criar um novo pagamento com sucesso")
    void criarPagamento_deveCriarNovoPagamentoComSucesso() {
        given()
            .contentType(ContentType.JSON)
            .body(validPagamentoDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.CREATED.value())
            .header("Location", containsString("/pagamentos/"))
            .body("id", notNullValue())
            .body("status", equalTo(Status.CRIADO.name()))
            .body("nome", equalTo(validPagamentoDto.getNome()));
    }

    @Test
    @DisplayName("POST /pagamentos - Deve retornar 400 Bad Request para DTO inválido (campos ausentes)")
    void criarPagamento_deveRetornarBadRequestParaDtoInvalido_camposAusentes() {
        PagamentoDto invalidDto = new PagamentoDto(); // DTO com campos nulos/vazios
        invalidDto.setNome(null); // Tornando um campo @NotBlank null

        given()
            .contentType(ContentType.JSON)
            .body(invalidDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("message", containsString("Validation failed"));
    
        invalidDto.setNome(""); // Tornando um campo @NotBlank vazio
         given()
            .contentType(ContentType.JSON)
            .body(invalidDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("message", containsString("Validation failed"));

        invalidDto.setNome("Nome Valido");
        invalidDto.setValor(null); // Campo @NotNull
         given()
            .contentType(ContentType.JSON)
            .body(invalidDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("message", containsString("Validation failed"));
    }

    @Test
    @DisplayName("POST /pagamentos - Deve retornar 400 Bad Request para DTO inválido (valores negativos/zero para valor)")
    void criarPagamento_deveRetornarBadRequestParaDtoInvalido_valorNegativoOuZero() {
        validPagamentoDto.setValor(BigDecimal.ZERO); // Valor Zero
        given()
            .contentType(ContentType.JSON)
            .body(validPagamentoDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("message", containsString("Validation failed"));
        
        validPagamentoDto.setValor(new BigDecimal("-10.00")); // Valor negativo
        given()
            .contentType(ContentType.JSON)
            .body(validPagamentoDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("message", containsString("Validation failed"));
    }

    @Test
    @DisplayName("POST /pagamentos - Deve retornar 400 Bad Request para DTO inválido (tamanho excedido)")
    void criarPagamento_deveRetornarBadRequestParaDtoInvalido_tamanhoExcedido() {
        validPagamentoDto.setNome("A".repeat(101)); // Campo 'nome' > 100 caracteres
        given()
            .contentType(ContentType.JSON)
            .body(validPagamentoDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("message", containsString("Validation failed"));
        
        validPagamentoDto.setNome("Nome Valido");
        validPagamentoDto.setNumero("1".repeat(20)); // Campo 'numero' > 19 caracteres
        given()
            .contentType(ContentType.JSON)
            .body(validPagamentoDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("message", containsString("Validation failed"));

         validPagamentoDto.setNumero("1234567890123456789");
         validPagamentoDto.setExpiracao("12/2025"); // Campo 'expiracao' > 7 caracteres
         given()
            .contentType(ContentType.JSON)
            .body(validPagamentoDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("message", containsString("Validation failed"));

        validPagamentoDto.setExpiracao("12/25");
        validPagamentoDto.setCodigo("1234"); // Campo 'codigo' > 3 caracteres
         given()
            .contentType(ContentType.JSON)
            .body(validPagamentoDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("message", containsString("Validation failed"));
    }

    @Test
    @DisplayName("POST /pagamentos - Deve retornar 400 Bad Request para DTO inválido (tamanho menor que necessário)")
    void criarPagamento_deveRetornarBadRequestParaDtoInvalido_tamanhoMenorQueNecessario() {
        validPagamentoDto.setCodigo("12"); // Campo 'codigo' < 3 caracteres
        given()
            .contentType(ContentType.JSON)
            .body(validPagamentoDto)
        .when()
            .post("/pagamentos")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("message", containsString("Validation failed"));
    }

    // --- PUT /pagamentos/{id} ---
    @Test
    @DisplayName("PUT /pagamentos/{id} - Deve atualizar um pagamento existente com sucesso")
    void atualizarPagamento_deveAtualizarPagamentoComSucesso() {
        Pagamento pagamentoSalvo = pagamentoRepository.save(new Pagamento(null, new BigDecimal("100.00"), "Cliente Antigo", "1111222233334444", "10/24", "111", Status.CRIADO, 1L, 1L));

        PagamentoDto dtoAtualizado = modelPagamentoParaDto(pagamentoSalvo);
        dtoAtualizado.setNome("Cliente Novo");
        dtoAtualizado.setValor(new BigDecimal("150.00"));

        given()
            .contentType(ContentType.JSON)
            .body(dtoAtualizado)
        .when()
            .put("/pagamentos/{id}", pagamentoSalvo.getId())
        .then()
            .statusCode(HttpStatus.OK.value())
            .body("id", equalTo(pagamentoSalvo.getId().intValue()))
            .body("nome", equalTo("Cliente Novo"))
            .body("valor", equalTo(150.0F));

        Optional<Pagamento> updatedPagamento = pagamentoRepository.findById(pagamentoSalvo.getId());
        assertTrue(updatedPagamento.isPresent());
        assertEquals("Cliente Novo", updatedPagamento.get().getNome());
        assertEquals(new BigDecimal("150.00"), updatedPagamento.get().getValor());
    }

    @Test
    @DisplayName("PUT /pagamentos/{id} - Deve retornar 404 Not Found ao tentar atualizar ID inexistente")
    void atualizarPagamento_deveRetornarNotFoundParaIdInexistente() {
        given()
            .contentType(ContentType.JSON)
            .body(validPagamentoDto)
        .when()
            .put("/pagamentos/{id}", 9999L)
        .then()
            .statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("PUT /pagamentos/{id} - Deve retornar 400 Bad Request para DTO inválido na atualização")
    void atualizarPagamento_deveRetornarBadRequestParaDtoInvalido() {
        Pagamento pagamentoSalvo = pagamentoRepository.save(new Pagamento(null, new BigDecimal("100.00"), "Cliente Existente", "1111222233334444", "10/24", "111", Status.CRIADO, 1L, 1L));

        PagamentoDto invalidDto = modelPagamentoParaDto(pagamentoSalvo);
        invalidDto.setNome(""); // Nome vazio

        given()
            .contentType(ContentType.JSON)
            .body(invalidDto)
        .when()
            .put("/pagamentos/{id}", pagamentoSalvo.getId())
        .then()
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .body("message", containsString("Validation failed"));
    }
    
    // --- DELETE /pagamentos/{id} ---
    @Test
    @DisplayName("DELETE /pagamentos/{id} - Deve remover um pagamento com sucesso")
    void removerPagamento_deveRemoverPagamentoComSucesso() {
        Pagamento pagamentoSalvo = pagamentoRepository.save(new Pagamento(null, new BigDecimal("300.00"), "Cliente a Remover", "0000000000000000", "02/26", "789", Status.CRIADO, 1L, 1L));

        given()
            .contentType(ContentType.JSON)
        .when()
            .delete("/pagamentos/{id}", pagamentoSalvo.getId())
        .then()
            .statusCode(HttpStatus.NO_CONTENT.value());

        Optional<Pagamento> deletedPagamento = pagamentoRepository.findById(pagamentoSalvo.getId());
        assertFalse(deletedPagamento.isPresent());
    }

    @Test
    @DisplayName("DELETE /pagamentos/{id} - Deve retornar 404 Not Found ao tentar remover ID inexistente")
    void removerPagamento_deveRetornarNotFoundParaIdInexistente() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .delete("/pagamentos/{id}", 9999L)
        .then()
            .statusCode(HttpStatus.NOT_FOUND.value());
    }

    // --- PATCH /pagamentos/{id}/confirmar ---
    @Test
    @DisplayName("PATCH /pagamentos/{id}/confirmar - Deve confirmar um pagamento com sucesso")
    void confirmarPagamento_deveConfirmarComSucesso() {
        Pagamento pagamentoSalvo = pagamentoRepository.save(new Pagamento(null, new BigDecimal("400.00"), "Pagamento Pendente", "1111111111111111", "03/27", "987", Status.CRIADO, 1L, 1L));

        given()
            .contentType(ContentType.JSON)
        .when()
            .patch("/pagamentos/{id}/confirmar", pagamentoSalvo.getId())
        .then()
            .statusCode(HttpStatus.OK.value()); // PATCH retorna 200 se a operação é bem-sucedida (void)

        Optional<Pagamento> confirmedPagamento = pagamentoRepository.findById(pagamentoSalvo.getId());
        assertTrue(confirmedPagamento.isPresent());
        assertEquals(Status.CONFIRMADO, confirmedPagamento.get().getStatus());
    }

    @Test
    @DisplayName("PATCH /pagamentos/{id}/confirmar - Deve retornar 404 Not Found para ID inexistente")
    void confirmarPagamento_deveRetornarNotFoundParaIdInexistente() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .patch("/pagamentos/{id}/confirmar", 9999L)
        .then()
            .statusCode(HttpStatus.NOT_FOUND.value());
    }

    // Helpers
    private PagamentoDto modelPagamentoParaDto(Pagamento pagamento) {
        PagamentoDto dto = new PagamentoDto();
        dto.setId(pagamento.getId());
        dto.setValor(pagamento.getValor());
        dto.setNome(pagamento.getNome());
        dto.setNumero(pagamento.getNumero());
        dto.setExpiracao(pagamento.getExpiracao());
        dto.setCodigo(pagamento.getCodigo());
        dto.setStatus(pagamento.getStatus());
        dto.setFormaDePagamentoId(pagamento.getFormaDePagamentoId());
        dto.setPedidoId(pagamento.getPedidoId());
        return dto;
    }
}