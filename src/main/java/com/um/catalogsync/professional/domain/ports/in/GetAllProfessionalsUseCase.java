package com.um.catalogsync.professional.domain.ports.in;

import com.um.catalogsync.professional.domain.model.Professional;
import java.util.List;

/** Puerto de entrada: listar todos los profesionales de la copia local. */
public interface GetAllProfessionalsUseCase {

    List<Professional> getAll();
}
