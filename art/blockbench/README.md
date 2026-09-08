# Swimming animation source

Open `swim_reference.bbmodel` in Blockbench. It contains the reference rig and both editable clips. Alternatively open `swim_reference.geo.json` as a Bedrock entity, switch to Animate, and import `../../src/main/resources/assets/morph/animations/swim.animation.json`.

The pig-shaped reference rig is an original authoring aid. The mod keeps Minecraft's actual meshes, textures, pivots and baby scaling. `animation.morph.quadruped_swim` is a one-second loop with opposing diagonal legs and a small head pitch cycle. Sprint swimming selects `animation.morph.quadruped_fast_swim`, a stronger 0.65-second cycle. Both clips were imported, played and saved in Blockbench 5.1.6.

The shared Java runtime reads rotation-only Bedrock JSON directly; no animation library or additional player installation is required. Tracks are XYZ degrees relative to each vanilla bone's rest rotation, converted to radians at runtime. The reference uses Bedrock Y-up coordinates; it previews the rhythm and relative bone movement rather than every species' exact native orientation.

Supported bones: `head`, `body`, `right_hind_leg`, `left_hind_leg`, `right_front_leg`, `left_front_leg`. Use numeric linear rotation keys, with matching values at time zero and the loop end. Scale, position, Molang expressions and animation controllers are not interpreted. Unknown bones are ignored. Invalid clips fall back to native poses.

Export to `src/main/resources/assets/morph/animations/swim.animation.json`, retaining both animation names. Resource reload (F3+T) reloads this asset on both loaders. Test water movement and return to land in game; the editor cannot validate deferred rendering or equipment layers.
