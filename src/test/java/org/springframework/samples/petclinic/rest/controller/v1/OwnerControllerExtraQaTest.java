package org.springframework.samples.petclinic.rest.controller.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.mapper.OwnerMapper;
import org.springframework.samples.petclinic.mapper.PetMapper;
import org.springframework.samples.petclinic.mapper.VisitMapper;
import org.springframework.samples.petclinic.model.Owner;
import org.springframework.samples.petclinic.rest.dto.OwnerDto;
import org.springframework.samples.petclinic.rest.dto.OwnerFieldsDto;
import org.springframework.samples.petclinic.service.ClinicService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerControllerExtraQaTest {

    private ClinicService clinicService;
    private OwnerMapper ownerMapper;
    private OwnerRestControllerV1 controller;

    @BeforeEach
    void configurar() {
        clinicService = mock(ClinicService.class);
        ownerMapper = mock(OwnerMapper.class);
        controller = new OwnerRestControllerV1(
                clinicService, ownerMapper, mock(PetMapper.class), mock(VisitMapper.class));
    }

    @Test
    @DisplayName("TA-21 | Buscar proprietario existente devolve 200 e DTO")
    void buscarProprietarioExistente() {
        Owner owner = new Owner();
        owner.setId(321);
        OwnerDto dto = mock(OwnerDto.class);
        when(clinicService.findOwnerById(321)).thenReturn(owner);
        when(ownerMapper.toOwnerDto(owner)).thenReturn(dto);

        ResponseEntity<OwnerDto> response = controller.getOwner(321);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody());
    }

    @Test
    @DisplayName("TA-22 | Buscar proprietario inexistente devolve 404")
    void buscarProprietarioInexistente() {
        when(clinicService.findOwnerById(999999)).thenReturn(null);
        assertEquals(HttpStatus.NOT_FOUND, controller.getOwner(999999).getStatusCode());
    }

    @Test
    @DisplayName("TA-23 | Lista vazia devolve 404 conforme contrato atual")
    void listaSemProprietarios() {
        when(clinicService.findAllOwners()).thenReturn(List.of());
        assertEquals(HttpStatus.NOT_FOUND, controller.listOwners(null).getStatusCode());
    }

    @Test
    @DisplayName("TA-24 | Filtro por sobrenome consulta servico corretamente")
    void filtroSobrenome() {
        when(clinicService.findOwnerByLastName("Silva")).thenReturn(List.of());
        controller.listOwners("Silva");
        verify(clinicService).findOwnerByLastName("Silva");
        verify(clinicService, never()).findAllOwners();
    }

    @Test
    @DisplayName("TA-25 | Cadastro chama servico e devolve 201 com Location")
    void cadastrarProprietario() {
        OwnerFieldsDto fields = mock(OwnerFieldsDto.class);
        Owner owner = new Owner();
        owner.setId(321);
        OwnerDto dto = mock(OwnerDto.class);
        when(ownerMapper.toOwner(fields)).thenReturn(owner);
        when(ownerMapper.toOwnerDto(owner)).thenReturn(dto);

        ResponseEntity<OwnerDto> result = controller.addOwner(fields);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(dto, result.getBody());
        assertNotNull(result.getHeaders().getLocation());
        assertTrue(result.getHeaders().getLocation().toString().endsWith("/api/owners/321"));
        verify(clinicService).saveOwner(owner);
    }

    @Test
    @DisplayName("TA-26 | Atualizar proprietario inexistente devolve 404")
    void atualizarInexistente() {
        when(clinicService.findOwnerById(999999)).thenReturn(null);
        ResponseEntity<OwnerDto> response = controller.updateOwner(999999, mock(OwnerFieldsDto.class));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(clinicService, never()).saveOwner(any(Owner.class));
    }

    @Test
    @DisplayName("TA-27 | Excluir proprietario inexistente devolve 404")
    void excluirInexistente() {
        when(clinicService.findOwnerById(999999)).thenReturn(null);
        assertEquals(HttpStatus.NOT_FOUND, controller.deleteOwner(999999).getStatusCode());
        verify(clinicService, never()).deleteOwner(any(Owner.class));
    }

    @Test
    @DisplayName("TA-29 | Lista populada retorna HTTP 200")
    void listaComProprietarios() {
        Owner owner = new Owner();
        owner.setId(321);
        OwnerDto dto = mock(OwnerDto.class);
        when(clinicService.findAllOwners()).thenReturn(List.of(owner));
        when(ownerMapper.toOwnerDtoCollection(anyCollection())).thenReturn(List.of(dto));
        ResponseEntity<List<OwnerDto>> response = controller.listOwners(null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("TA-30 | Atualizacao existente salva os campos informados")
    void atualizarProprietarioExistente() {
        Owner owner = new Owner();
        owner.setId(321);
        OwnerFieldsDto fields = mock(OwnerFieldsDto.class);
        when(clinicService.findOwnerById(321)).thenReturn(owner);
        when(fields.getFirstName()).thenReturn("Marina");
        when(fields.getLastName()).thenReturn("Silva");
        when(fields.getAddress()).thenReturn("Rua Nova");
        when(fields.getCity()).thenReturn("Santos");
        when(fields.getTelephone()).thenReturn("11987654321");
        ResponseEntity<OwnerDto> response = controller.updateOwner(321, fields);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals("Marina", owner.getFirstName());
        assertEquals("11987654321", owner.getTelephone());
        verify(clinicService).saveOwner(owner);
    }

    @Test
    @DisplayName("TA-31 | Filtro populado retorna HTTP 200")
    void filtroSobrenomeComResultados() {
        Owner owner = new Owner();
        owner.setLastName("Silva");
        when(clinicService.findOwnerByLastName("Silva")).thenReturn(List.of(owner));
        when(ownerMapper.toOwnerDtoCollection(anyCollection())).thenReturn(List.of(mock(OwnerDto.class)));
        assertEquals(HttpStatus.OK, controller.listOwners("Silva").getStatusCode());
        verify(clinicService, never()).findAllOwners();
    }

    @Test
    @DisplayName("TA-28 | Excluir proprietario existente chama servico e devolve 204")
    void excluirExistente() {
        Owner owner = new Owner();
        owner.setId(321);
        when(clinicService.findOwnerById(321)).thenReturn(owner);
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteOwner(321).getStatusCode());
        verify(clinicService).deleteOwner(owner);
    }
}
