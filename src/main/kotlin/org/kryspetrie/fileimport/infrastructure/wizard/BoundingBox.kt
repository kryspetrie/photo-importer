package org.kryspetrie.fileimport.infrastructure.wizard

/**
 * Re-exports of geometry types from domain layer. These typealiases maintain backward compatibility
 * while the canonical definitions live in [org.kryspetrie.fileimport.domain.model.geometry].
 */
typealias Point = org.kryspetrie.fileimport.domain.model.geometry.Point

typealias BoundingBoxCorners = org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxCorners

typealias Corner = org.kryspetrie.fileimport.domain.model.geometry.Corner

typealias BoundingBox = org.kryspetrie.fileimport.domain.model.geometry.BoundingBox
