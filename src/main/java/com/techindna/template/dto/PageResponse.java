package com.techindna.template.dto;

import java.util.List;

public record PageResponse<T>(List<T> data, Meta meta) {}
