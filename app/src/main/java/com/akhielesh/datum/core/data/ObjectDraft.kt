package com.akhielesh.datum.core.data

import com.akhielesh.datum.core.geometry.FusedDims
import com.akhielesh.datum.core.geometry.ObjectFusion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The object currently being measured, accumulated across AR, motion and photo sessions. */
data class ObjectDraft(
    val name: String = "Object",
    val shape: ShapeKind = ShapeKind.BOX,
    val estimates: List<DimEstimate> = emptyList(),
    val ratios: List<RatioConstraint> = emptyList(),
    val textures: Map<Face, String> = emptyMap(),
    val material: String = "Cardboard box",
    /** Library id once saved, so re-saving updates rather than duplicates. */
    val savedId: String? = null,
    /** Bumps whenever new evidence arrives, to animate the "re-calibrated" moment. */
    val revision: Int = 0,
) {
    val fused: FusedDims get() = ObjectFusion.fuse(estimates, ratios, shape)

    val isEmpty: Boolean get() = estimates.isEmpty() && ratios.isEmpty()

    fun toModel(): ObjectModel? {
        val f = fused
        val l = f.l ?: return null
        val w = f.w ?: return null
        val h = f.h ?: return null
        return ObjectModel(
            name = name, shape = shape,
            length = l.value, width = w.value, height = h.value,
            sigmaL = l.sigma, sigmaW = w.sigma, sigmaH = h.sigma,
            estimates = estimates, ratios = ratios, textures = textures, material = material,
        )
    }

    companion object {
        fun from(model: ObjectModel, savedId: String?) = ObjectDraft(
            name = model.name,
            shape = model.shape,
            estimates = model.estimates.ifEmpty {
                listOf(
                    DimEstimate(Dim.L, model.length, model.sigmaL.coerceAtLeast(1e-3), EstimateSource.MANUAL),
                    DimEstimate(Dim.W, model.width, model.sigmaW.coerceAtLeast(1e-3), EstimateSource.MANUAL),
                    DimEstimate(Dim.H, model.height, model.sigmaH.coerceAtLeast(1e-3), EstimateSource.MANUAL),
                )
            },
            ratios = model.ratios,
            textures = model.textures,
            material = model.material,
            savedId = savedId,
        )
    }
}

class ObjectDraftStore {
    private val _draft = MutableStateFlow(ObjectDraft())
    val draft: StateFlow<ObjectDraft> = _draft.asStateFlow()

    fun addEstimates(list: List<DimEstimate>) = _draft.update { it.copy(estimates = it.estimates + list, revision = it.revision + 1) }

    /** Replaces earlier estimates from the same source for the same dimensions (a re-measure). */
    fun replaceEstimates(source: EstimateSource, list: List<DimEstimate>) = _draft.update { d ->
        val dims = list.map { it.dim }.toSet()
        d.copy(
            estimates = d.estimates.filterNot { it.source == source && it.dim in dims } + list,
            revision = d.revision + 1,
        )
    }

    fun removeEstimate(e: DimEstimate) = _draft.update { it.copy(estimates = it.estimates - e, revision = it.revision + 1) }

    fun addRatio(r: RatioConstraint) = _draft.update { it.copy(ratios = it.ratios + r, revision = it.revision + 1) }

    fun removeRatio(r: RatioConstraint) = _draft.update { it.copy(ratios = it.ratios - r, revision = it.revision + 1) }

    fun setTexture(face: Face, path: String) = _draft.update { it.copy(textures = it.textures + (face to path)) }

    fun setShape(shape: ShapeKind) = _draft.update { it.copy(shape = shape, revision = it.revision + 1) }

    fun setName(name: String) = _draft.update { it.copy(name = name) }

    fun setMaterial(material: String) = _draft.update { it.copy(material = material) }

    fun markSaved(id: String) = _draft.update { it.copy(savedId = id) }

    fun load(draft: ObjectDraft) {
        _draft.value = draft
    }

    fun clear() {
        _draft.value = ObjectDraft()
    }
}
