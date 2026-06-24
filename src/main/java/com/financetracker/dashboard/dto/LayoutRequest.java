package com.financetracker.dashboard.dto;

import java.util.List;

public record LayoutRequest(
        List<String> ordemWidgets,
        List<String> widgetsOcultos
) {}