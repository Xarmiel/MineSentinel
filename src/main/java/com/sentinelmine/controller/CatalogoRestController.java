package com.sentinelmine.controller;

import com.sentinelmine.entity.CatalogoAnomalias;
import com.sentinelmine.entity.CatalogoEPP;
import com.sentinelmine.entity.RolPersonal;
import com.sentinelmine.entity.Turno;
import com.sentinelmine.repository.CatalogoAnomaliasRepository;
import com.sentinelmine.repository.CatalogoEPPRepository;
import com.sentinelmine.repository.RolPersonalRepository;
import com.sentinelmine.repository.TurnoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para la consulta de catálogos maestros del sistema de seguridad minera.
 */
@RestController
@RequestMapping("/api/v1/catalogos")
@CrossOrigin(origins = "*")
public class CatalogoRestController {

    private final RolPersonalRepository rolPersonalRepository;
    private final TurnoRepository turnoRepository;
    private final CatalogoEPPRepository catalogoEPPRepository;
    private final CatalogoAnomaliasRepository catalogoAnomaliasRepository;

    public CatalogoRestController(RolPersonalRepository rolPersonalRepository,
                                  TurnoRepository turnoRepository,
                                  CatalogoEPPRepository catalogoEPPRepository,
                                  CatalogoAnomaliasRepository catalogoAnomaliasRepository) {
        this.rolPersonalRepository = rolPersonalRepository;
        this.turnoRepository = turnoRepository;
        this.catalogoEPPRepository = catalogoEPPRepository;
        this.catalogoAnomaliasRepository = catalogoAnomaliasRepository;
    }

    @GetMapping("/roles")
    public ResponseEntity<List<RolPersonal>> listarRoles() {
        return ResponseEntity.ok(rolPersonalRepository.findAll());
    }

    @GetMapping("/turnos")
    public ResponseEntity<List<Turno>> listarTurnos() {
        return ResponseEntity.ok(turnoRepository.findAll());
    }

    @GetMapping("/epp")
    public ResponseEntity<List<CatalogoEPP>> listarCatalogoEPP() {
        return ResponseEntity.ok(catalogoEPPRepository.findAll());
    }

    @GetMapping("/anomalias")
    public ResponseEntity<List<CatalogoAnomalias>> listarCatalogoAnomalias() {
        return ResponseEntity.ok(catalogoAnomaliasRepository.findAll());
    }
}
