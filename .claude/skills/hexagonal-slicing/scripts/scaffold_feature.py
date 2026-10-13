#!/usr/bin/env python3
"""
Scaffold a full hexagonal-architecture vertical slice (Spring Boot, Java) for
one feature/entity, reproducing the exact package layout, naming, and
annotations of the reference "hexagonal" project documented in
references/architecture-deep-dive.md.

Usage:
    python3 scaffold_feature.py \\
        --base-package com.example.hexagonal \\
        --feature Product \\
        --fields "description:String,price:BigDecimal" \\
        --src-root src/main/java \\
        --route /products

Run with --dry-run to preview the list of files that would be written
without touching the filesystem. Existing files are never overwritten
unless --force is passed.
"""
import argparse
import os
import re
import sys

NUMERIC_TYPES = {
    "BigDecimal", "Integer", "Long", "Double", "Float", "Short", "Byte",
    "int", "long", "double", "float", "short", "byte",
}

TYPE_IMPORTS = {
    "BigDecimal": "java.math.BigDecimal",
    "BigInteger": "java.math.BigInteger",
    "LocalDate": "java.time.LocalDate",
    "LocalDateTime": "java.time.LocalDateTime",
    "LocalTime": "java.time.LocalTime",
    "Instant": "java.time.Instant",
    "UUID": "java.util.UUID",
}

IRREGULAR_PLURALS = {
    "person": "people",
    "child": "children",
    "category": "categories",
}


def decapitalize(s: str) -> str:
    return s[0].lower() + s[1:] if s else s


def capitalize(s: str) -> str:
    return s[0].upper() + s[1:] if s else s


def pascal_to_snake(name: str) -> str:
    s1 = re.sub(r"(.)([A-Z][a-z]+)", r"\1_\2", name)
    s2 = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", s1)
    return s2.lower()


def naive_pluralize(word: str) -> str:
    lower = word.lower()
    if lower in IRREGULAR_PLURALS:
        plural_lower = IRREGULAR_PLURALS[lower]
        return capitalize(plural_lower) if word[0].isupper() else plural_lower
    if re.search(r"[sxz]$", word) or re.search(r"(ch|sh)$", word):
        return word + "es"
    if re.search(r"[^aeiou]y$", word):
        return word[:-1] + "ies"
    return word + "s"


class Field:
    def __init__(self, raw_name: str, raw_type: str):
        self.name = decapitalize(raw_name.strip())
        self.type = raw_type.strip()
        self.label = capitalize(self.name)

    @property
    def import_line(self):
        return TYPE_IMPORTS.get(self.type)

    def declaration(self) -> str:
        return f"    private {self.type} {self.name};"

    def request_annotations(self) -> list[str]:
        if self.type == "String":
            return [f'    @NotBlank(message = "{self.label} is mandatory")']
        if self.type == "BigDecimal":
            return [
                f'    @NotNull(message = "{self.label} is mandatory")',
                f'    @DecimalMin(value = "0.0", inclusive = false, message = "{self.label} must be greater than 0")',
            ]
        if self.type in NUMERIC_TYPES:
            return [f'    @NotNull(message = "{self.label} is mandatory")']
        return [f'    @NotNull(message = "{self.label} is mandatory")']

    def request_field_block(self) -> str:
        lines = self.request_annotations()
        lines.append(f"    private {self.type} {self.name};")
        return "\n".join(lines)


def parse_fields(spec: str) -> list[Field]:
    fields = []
    if not spec:
        return fields
    for chunk in spec.split(","):
        chunk = chunk.strip()
        if not chunk:
            continue
        if ":" not in chunk:
            raise ValueError(f"Invalid field spec '{chunk}', expected name:Type")
        name, type_ = chunk.split(":", 1)
        fields.append(Field(name, type_))
    return fields


def extra_imports(fields: list[Field]) -> list[str]:
    seen = sorted({f.import_line for f in fields if f.import_line})
    return [f"import {imp};" for imp in seen]


def needs_validation_import(fields: list[Field]) -> bool:
    return True  # request DTO always has at least @NotNull/@NotBlank per field or id-less body


def render(template: str, **kwargs) -> str:
    return template.format(**kwargs)


def build_files(base_package: str, feature: str, plural: str, fields: list[Field], route: str) -> dict:
    feature_lower_pkg = feature.lower()
    f_lower = decapitalize(feature)          # e.g. product
    f_plural_lower = decapitalize(plural)    # e.g. products
    table_name = pascal_to_snake(plural)     # e.g. products / order_items
    root = f"{base_package}.{feature_lower_pkg}"
    imports_block = "\n".join(extra_imports(fields))
    imports_block = (imports_block + "\n\n") if imports_block else ""
    field_decls = "\n".join(f.declaration() for f in fields) if fields else ""
    request_field_blocks = "\n\n".join(f.request_field_block() for f in fields) if fields else ""
    response_field_decls = "\n".join(f.declaration() for f in fields) if fields else ""

    def mapper_field_lines(getter_prefix_src: str) -> str:
        return "\n".join(f"                .{f.name}({getter_prefix_src}.get{capitalize(f.name)}())" for f in fields)

    files = {}

    # ---------- domain/model ----------
    files[f"domain/model/{feature}.java"] = f"""package {root}.domain.model;

{imports_block}import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {feature} {{
    private Long id;
{field_decls}
}}
"""

    # ---------- domain/ports/in ----------
    files[f"domain/ports/in/Create{feature}UseCase.java"] = f"""package {root}.domain.ports.in;

import {root}.domain.model.{feature};

public interface Create{feature}UseCase {{
    {feature} create{feature}({feature} {f_lower});
}}
"""

    files[f"domain/ports/in/Get{feature}ByIdUseCase.java"] = f"""package {root}.domain.ports.in;

import {root}.domain.model.{feature};
import java.util.Optional;

public interface Get{feature}ByIdUseCase {{

    Optional<{feature}> get{feature}ById(Long id);

}}
"""

    files[f"domain/ports/in/GetAll{plural}UseCase.java"] = f"""package {root}.domain.ports.in;

import {root}.domain.model.{feature};

import java.util.List;

public interface GetAll{plural}UseCase {{
    List<{feature}> getAll{plural}();
}}
"""

    files[f"domain/ports/in/Update{feature}UseCase.java"] = f"""package {root}.domain.ports.in;

import {root}.domain.model.{feature};
import java.util.Optional;

public interface Update{feature}UseCase {{
    Optional<{feature}> update{feature}(Long id, {feature} {f_lower});
}}
"""

    files[f"domain/ports/in/Delete{feature}UseCase.java"] = f"""package {root}.domain.ports.in;

public interface Delete{feature}UseCase {{
    void delete{feature}(Long id);
}}
"""

    # ---------- domain/ports/out ----------
    files[f"domain/ports/out/{feature}Repository.java"] = f"""package {root}.domain.ports.out;

import {root}.domain.model.{feature};

import java.util.List;
import java.util.Optional;

public interface {feature}Repository {{

    {feature} create({feature} {f_lower});
    Optional<{feature}> findById(Long id);
    List<{feature}> findAll();
    Optional<{feature}> update(Long id, {feature} {f_lower});
    boolean deleteById(Long id);

}}
"""

    # ---------- application/exception ----------
    files[f"application/exception/{feature}Exception.java"] = f"""package {root}.application.exception;

public class {feature}Exception extends RuntimeException {{
    public {feature}Exception(Long id) {{
        super("Could not find {f_lower} with id: " + id);
    }}
}}
"""

    # ---------- application/usecases ----------
    files[f"application/usecases/Create{feature}UseCaseImpl.java"] = f"""package {root}.application.usecases;

import {root}.domain.model.{feature};
import {root}.domain.ports.in.Create{feature}UseCase;
import {root}.domain.ports.out.{feature}Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Create{feature}UseCaseImpl implements Create{feature}UseCase {{

    private final {feature}Repository {f_lower}Repository;

    @Override
    public {feature} create{feature}({feature} {f_lower}) {{
        return {f_lower}Repository.create({f_lower});
    }}

}}
"""

    files[f"application/usecases/Get{feature}ByIdUseCaseImpl.java"] = f"""package {root}.application.usecases;

import {root}.domain.model.{feature};
import {root}.domain.ports.in.Get{feature}ByIdUseCase;
import {root}.domain.ports.out.{feature}Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class Get{feature}ByIdUseCaseImpl implements Get{feature}ByIdUseCase {{

    private final {feature}Repository repository;

    @Override
    public Optional<{feature}> get{feature}ById(Long id) {{
        return repository.findById(id);
    }}

}}
"""

    files[f"application/usecases/GetAll{plural}UseCaseImpl.java"] = f"""package {root}.application.usecases;

import {root}.domain.model.{feature};
import {root}.domain.ports.in.GetAll{plural}UseCase;
import {root}.domain.ports.out.{feature}Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetAll{plural}UseCaseImpl implements GetAll{plural}UseCase {{

    private final {feature}Repository {f_lower}Repository;

    @Override
    public List<{feature}> getAll{plural}() {{
        return {f_lower}Repository.findAll();
    }}
}}
"""

    files[f"application/usecases/Update{feature}UseCaseImpl.java"] = f"""package {root}.application.usecases;

import {root}.domain.model.{feature};
import {root}.domain.ports.in.Update{feature}UseCase;
import {root}.domain.ports.out.{feature}Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class Update{feature}UseCaseImpl implements Update{feature}UseCase {{

    private final {feature}Repository repository;

    @Override
    public Optional<{feature}> update{feature}(Long id, {feature} {f_lower}) {{
        return repository.update(id, {f_lower});
    }}
}}
"""

    files[f"application/usecases/Delete{feature}UseCaseImpl.java"] = f"""package {root}.application.usecases;

import {root}.domain.ports.in.Delete{feature}UseCase;
import {root}.domain.ports.out.{feature}Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Delete{feature}UseCaseImpl implements Delete{feature}UseCase {{

    private final {feature}Repository repository;

    @Override
    public void delete{feature}(Long id) {{
        repository.deleteById(id);
    }}

}}
"""

    # ---------- application/service ----------
    files[f"application/service/{feature}Service.java"] = f"""package {root}.application.service;

import {root}.application.exception.{feature}Exception;
import {root}.domain.model.{feature};
import {root}.domain.ports.in.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class {feature}Service {{

    private final Create{feature}UseCase create{feature}UseCase;
    private final Get{feature}ByIdUseCase get{feature}ByIdUseCase;
    private final GetAll{plural}UseCase getAll{plural}UseCase;
    private final Update{feature}UseCase update{feature}UseCase;
    private final Delete{feature}UseCase delete{feature}UseCase;

    public {feature} create{feature}({feature} {f_lower}) {{
        return create{feature}UseCase.create{feature}({f_lower});
    }}

    public {feature} get{feature}ById(Long id) {{
        return get{feature}ByIdUseCase.get{feature}ById(id)
                .orElseThrow(() -> new {feature}Exception(id));
    }}

    public List<{feature}> getAll{plural}() {{
        return getAll{plural}UseCase.getAll{plural}();
    }}

    public {feature} update{feature}(Long id, {feature} {f_lower}) {{
        return update{feature}UseCase.update{feature}(id, {f_lower})
                .orElseThrow(() -> new {feature}Exception(id));
    }}

    public void delete{feature}(Long id) {{
        delete{feature}UseCase.delete{feature}(id);
    }}

}}
"""

    # ---------- infrastructure/persistence/entity ----------
    files[f"infrastructure/persistence/entity/{feature}Entity.java"] = f"""package {root}.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

{imports_block}@Getter
@Setter
@Entity
@Table(name = "{table_name}")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {feature}Entity {{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
{field_decls}
}}
"""

    # ---------- infrastructure/persistence/mapper ----------
    to_entity_fields = mapper_field_lines(f_lower)
    to_domain_fields = mapper_field_lines("entity")
    files[f"infrastructure/persistence/mapper/{feature}Mapper.java"] = f"""package {root}.infrastructure.persistence.mapper;

import {root}.domain.model.{feature};
import {root}.infrastructure.persistence.entity.{feature}Entity;
import org.springframework.stereotype.Component;

@Component
public class {feature}Mapper {{

    public {feature}Entity toEntity({feature} {f_lower}) {{
        if ({f_lower} == null) {{
            return null;
        }}
        return {feature}Entity.builder()
                .id({f_lower}.getId())
{to_entity_fields}
                .build();
    }}

    public {feature} toDomainModel({feature}Entity entity) {{
        if (entity == null) {{
            return null;
        }}
        return {feature}.builder()
                .id(entity.getId())
{to_domain_fields}
                .build();
    }}
}}
"""

    # ---------- infrastructure/persistence/repository ----------
    files[f"infrastructure/persistence/repository/Jpa{feature}Repository.java"] = f"""package {root}.infrastructure.persistence.repository;

import {root}.infrastructure.persistence.entity.{feature}Entity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Jpa{feature}Repository extends JpaRepository<{feature}Entity, Long> {{
}}
"""

    # ---------- infrastructure/persistence/adapter ----------
    files[f"infrastructure/persistence/adapter/Jpa{feature}RepositoryAdapter.java"] = f"""package {root}.infrastructure.persistence.adapter;

import {root}.domain.model.{feature};
import {root}.domain.ports.out.{feature}Repository;
import {root}.infrastructure.persistence.entity.{feature}Entity;
import {root}.infrastructure.persistence.mapper.{feature}Mapper;
import {root}.infrastructure.persistence.repository.Jpa{feature}Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class Jpa{feature}RepositoryAdapter implements {feature}Repository {{

    private final Jpa{feature}Repository jpa{feature}Repository;
    private final {feature}Mapper {f_lower}Mapper;

    @Override
    public {feature} create({feature} {f_lower}) {{
        {feature}Entity {f_lower}Entity = {f_lower}Mapper.toEntity({f_lower});
        {feature}Entity savedEntity = jpa{feature}Repository.save({f_lower}Entity);
        return {f_lower}Mapper.toDomainModel(savedEntity);
    }}

    @Override
    public Optional<{feature}> findById(Long id) {{
        return jpa{feature}Repository.findById(id)
                .map({f_lower}Mapper::toDomainModel);
    }}

    @Override
    public List<{feature}> findAll() {{
        return jpa{feature}Repository.findAll().stream()
                .map({f_lower}Mapper::toDomainModel)
                .collect(Collectors.toList());
    }}

    @Override
    public Optional<{feature}> update(Long id, {feature} {f_lower}) {{
        if (jpa{feature}Repository.existsById(id)) {{
            {feature}Entity {f_lower}Entity = {f_lower}Mapper.toEntity({f_lower});
            {f_lower}Entity.setId(id); // Ensure the ID is set for update
            {feature}Entity updatedEntity = jpa{feature}Repository.save({f_lower}Entity);
            return Optional.of({f_lower}Mapper.toDomainModel(updatedEntity));
        }}
        return Optional.empty();
    }}

    @Override
    public boolean deleteById(Long id) {{
        if (jpa{feature}Repository.existsById(id)) {{
            jpa{feature}Repository.deleteById(id);
            return true;
        }}
        return false;
    }}
}}
"""

    # ---------- infrastructure/web/dto ----------
    files[f"infrastructure/web/dto/{feature}Request.java"] = f"""package {root}.infrastructure.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

{imports_block}@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {feature}Request {{

{request_field_blocks}
}}
"""

    files[f"infrastructure/web/dto/{feature}Response.java"] = f"""package {root}.infrastructure.web.dto;

import lombok.*;

{imports_block}@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {feature}Response {{
    private Long id;
{response_field_decls}
}}
"""

    # ---------- infrastructure/web/mapper ----------
    to_domain_from_request = mapper_field_lines("request")
    to_response_from_domain = mapper_field_lines("domain")
    files[f"infrastructure/web/mapper/{feature}DtoMapper.java"] = f"""package {root}.infrastructure.web.mapper;

import {root}.domain.model.{feature};
import {root}.infrastructure.web.dto.{feature}Request;
import {root}.infrastructure.web.dto.{feature}Response;
import org.springframework.stereotype.Component;

@Component
public class {feature}DtoMapper {{

    public {feature} toDomain({feature}Request request) {{
        if (request == null) {{
            return null;
        }}
        return {feature}.builder()
{to_domain_from_request}
                .build();
    }}

    public {feature}Response toResponse({feature} domain) {{
        if (domain == null) {{
            return null;
        }}
        return {feature}Response.builder()
                .id(domain.getId())
{to_response_from_domain}
                .build();
    }}
}}
"""

    # ---------- infrastructure/web/controller ----------
    files[f"infrastructure/web/controller/{feature}Controller.java"] = f"""package {root}.infrastructure.web.controller;

import {root}.application.exception.{feature}Exception;
import {root}.application.service.{feature}Service;
import {root}.domain.model.{feature};
import {root}.domain.ports.in.*;
import {root}.infrastructure.web.dto.{feature}Request;
import {root}.infrastructure.web.dto.{feature}Response;
import {root}.infrastructure.web.mapper.{feature}DtoMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("{route}")
@RequiredArgsConstructor
public class {feature}Controller {{

    private final {feature}Service {f_lower}Service;
    private final {feature}DtoMapper {f_lower}DtoMapper;

    @PostMapping
    public ResponseEntity<{feature}Response> create{feature}(@Valid @RequestBody {feature}Request {f_lower}Request) {{
        {feature} {f_lower} = {f_lower}DtoMapper.toDomain({f_lower}Request);
        {feature} created{feature} = {f_lower}Service.create{feature}({f_lower});
        return new ResponseEntity<>({f_lower}DtoMapper.toResponse(created{feature}), HttpStatus.CREATED);
    }}

    @GetMapping("/{{id}}")
    public ResponseEntity<{feature}Response> get{feature}ById(@PathVariable Long id) {{
        try {{
            return ResponseEntity.ok({f_lower}DtoMapper.toResponse({f_lower}Service.get{feature}ById(id)));
        }} catch ({feature}Exception e) {{
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }}
    }}

    @GetMapping
    public ResponseEntity<List<{feature}Response>> getAll{plural}() {{
        List<{feature}Response> {f_lower}Responses = {f_lower}Service.getAll{plural}().stream()
                .map({f_lower}DtoMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok({f_lower}Responses);
    }}

    @PutMapping("/{{id}}")
    public ResponseEntity<{feature}Response> update{feature}(@PathVariable Long id, @Valid @RequestBody {feature}Request {f_lower}Request) {{
        {feature} {f_lower} = {f_lower}DtoMapper.toDomain({f_lower}Request);
        try {{
            return ResponseEntity.ok({f_lower}DtoMapper.toResponse({f_lower}Service.update{feature}(id, {f_lower})));
        }} catch ({feature}Exception e) {{
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }}
    }}

    @DeleteMapping("/{{id}}")
    public ResponseEntity<Void> delete{feature}(@PathVariable Long id) {{
        {f_lower}Service.delete{feature}(id);
        return ResponseEntity.noContent().build();
    }}
}}
"""

    return files


def main():
    parser = argparse.ArgumentParser(description="Scaffold a hexagonal-architecture vertical slice.")
    parser.add_argument("--base-package", required=True, help="e.g. com.example.hexagonal")
    parser.add_argument("--feature", required=True, help="Singular PascalCase entity name, e.g. Product")
    parser.add_argument("--plural", default=None, help="Override plural form, e.g. Categories for Category")
    parser.add_argument("--fields", default="", help='Comma-separated name:Type list, e.g. "description:String,price:BigDecimal"')
    parser.add_argument("--src-root", default="src/main/java", help="Java source root (default: src/main/java)")
    parser.add_argument("--route", default=None, help="Base REST path, default /<feature-plural-lowercase>")
    parser.add_argument("--force", action="store_true", help="Overwrite existing files")
    parser.add_argument("--dry-run", action="store_true", help="Print the file list without writing")
    args = parser.parse_args()

    feature = capitalize(args.feature.strip())
    plural = capitalize(args.plural.strip()) if args.plural else naive_pluralize(feature)
    fields = parse_fields(args.fields)
    route = args.route or f"/{decapitalize(plural)}"

    files = build_files(args.base_package, feature, plural, fields, route)

    package_path = args.base_package.replace(".", "/") + "/" + feature.lower()
    src_root = args.src_root.rstrip("/")

    written = []
    skipped = []
    for rel_path, content in files.items():
        full_path = os.path.join(src_root, package_path, rel_path)
        if args.dry_run:
            print(full_path)
            continue
        if os.path.exists(full_path) and not args.force:
            skipped.append(full_path)
            continue
        os.makedirs(os.path.dirname(full_path), exist_ok=True)
        with open(full_path, "w") as f:
            f.write(content)
        written.append(full_path)

    if args.dry_run:
        print(f"\n{len(files)} files would be generated under {os.path.join(src_root, package_path)}")
        return

    for path in written:
        print(f"created  {path}")
    for path in skipped:
        print(f"skipped  {path}  (already exists, use --force to overwrite)")

    print(f"\n{len(written)} files created, {len(skipped)} skipped.")
    if skipped:
        print("Re-run with --force to overwrite the skipped files.")


if __name__ == "__main__":
    sys.exit(main())
