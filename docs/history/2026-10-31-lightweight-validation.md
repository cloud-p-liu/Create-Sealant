# Lightweight mode and visual validation

LIGHTWEIGHT became the default sealed-dry displacement mode, independent of Field V3. It uses deduplicated chamber union OBBs, submerged-volume centroids, and distributed implicit drag. Field state is paused and retained, not cleared or averaged. Carrier solids outside the chamber domain are not separately counted.

The first sea-mask test failed even though an OBB and shader existed. Updating cached Veil uniforms after the complete ShaderInstance.apply path and explicitly uploading them produced a GPU-read-back mask count of one. The user then confirmed sea culling. A later explicit confirmation also covered exact-eye lightweight breathing and the original inset-entry/original-box-exit rule.

Those confirmations do not establish Sodium/Iris support, finite oxygen, FIELD swimming, or the later complete-entry policy. Original local load/build paths and transcript repetitions are omitted from this English decision record. Current contracts are in [architecture](../architecture/chambers.md).
