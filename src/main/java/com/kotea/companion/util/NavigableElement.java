package com.kotea.companion.util;

import org.jetbrains.kotlin.psi.KtClassOrObject;

public record NavigableElement(KoTEAElementKind kind, KtClassOrObject anchor) {
}
