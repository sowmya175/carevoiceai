package com.carevoice.dto.proposal;

import java.util.List;

public record ReorderRequest(List<Long> questionIds) {}
