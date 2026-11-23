package com.pelmanager.controller;

import com.pelmanager.entity.Pagamento;
import com.pelmanager.service.PagamentoService;
import com.pelmanager.service.PerfilPeladaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentoService;
    private final PerfilPeladaService perfilPeladaService;

    public PagamentoController(PagamentoService pagamentoService, PerfilPeladaService perfilPeladaService) {
        this.pagamentoService = pagamentoService;
        this.perfilPeladaService = perfilPeladaService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pagamento> obterPagamento(@PathVariable Long id) {
        Pagamento p = pagamentoService.obterPagamento(id);
        return ResponseEntity.ok(p);
    }

    @GetMapping("/{id}/qr-code")
    public ResponseEntity<Map<String, String>> obterQRCode(@PathVariable Long id) {
        try {
            Pagamento p = pagamentoService.obterPagamento(id);
            String qrCode = pagamentoService.gerarQrCode(id);
            String copiaECola = pagamentoService.gerarCopiaECola(id);
            String chavePix = "";
            if (p.getRodada() != null && p.getRodada().getGrupo() != null) {
                String chave = perfilPeladaService.configuracaoDe(p.getRodada().getGrupo()).getChavePixGerente();
                chavePix = chave != null ? chave : "";
            }
            String valor = p.getValor() != null ? p.getValor().toPlainString() : "0.00";
            return ResponseEntity.ok(Map.of(
                    "qrCode", qrCode,
                    "copiaECola", copiaECola,
                    "chavePix", chavePix,
                    "valor", valor,
                    "mensagem", "Escaneie para pagar via PIX"
            ));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Erro ao gerar QR Code";
            return ResponseEntity.badRequest().body(
                    Map.of("erro", msg, "mensagem", msg)
            );
        }
    }

    @GetMapping("/rodada/{rodadaId}")
    public ResponseEntity<List<Pagamento>> obterRodada(@PathVariable Long rodadaId) {
        return ResponseEntity.ok(pagamentoService.obterPagamentosRodada(rodadaId));
    }

    @GetMapping("/rodada/{rodadaId}/pendentes")
    public ResponseEntity<List<Pagamento>> obterPendentes(@PathVariable Long rodadaId) {
        return ResponseEntity.ok(pagamentoService.obterPendentes(rodadaId));
    }

    @PostMapping("/rodada/{rodadaId}/gerar")
    public ResponseEntity<List<Pagamento>> gerarPagamentosRodada(@PathVariable Long rodadaId,
                                                                 @RequestParam("usuarioId") Long usuarioId) {
        return ResponseEntity.ok(pagamentoService.gerarPagamentosComoAdmin(rodadaId, usuarioId));
    }

    @PostMapping("/rodada/{rodadaId}/desistencia/{usuarioId}")
    public ResponseEntity<List<Pagamento>> registrarDesistencia(@PathVariable Long rodadaId,
                                                                @PathVariable Long usuarioId,
                                                                @RequestParam("adminId") Long adminId) {
        return ResponseEntity.ok(pagamentoService.registrarDesistencia(rodadaId, usuarioId, adminId));
    }

    @PostMapping("/{id}/marcar-pago")
    public ResponseEntity<Map<String, String>> marcarPago(@PathVariable Long id,
                                                          @RequestParam("usuarioId") Long usuarioId) {
        pagamentoService.marcarPago(id, usuarioId);
        return ResponseEntity.ok(Map.of(
                "mensagem", "Pagamento marcado como recebido"
        ));
    }
}
