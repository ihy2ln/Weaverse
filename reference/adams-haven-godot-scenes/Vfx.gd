# VFX using the Kenney Particle Pack (CC0, addons/kenney_particle_pack).
# Element-aware: each element picks the texture family that reads as that element.
extends Node
class_name Vfx

const P := "res://addons/kenney_particle_pack/%s.png"

# element -> [texture family, tint]
const ELEMENT_FX := {
	ElementChart.E.FIRE:      ["flame_05", Color(1.0, 0.55, 0.25)],
	ElementChart.E.WATER:     ["circle_05", Color(0.4, 0.7, 1.0)],
	ElementChart.E.WIND:      ["trace_01", Color(0.5, 1.0, 0.8)],
	ElementChart.E.EARTH:     ["dirt_02", Color(0.75, 0.6, 0.35)],
	ElementChart.E.LIGHTNING: ["spark_05", Color(1.0, 0.95, 0.4)],
	ElementChart.E.LIGHT:     ["flare_01", Color(1.0, 0.98, 0.85)],
	ElementChart.E.DARK:      ["smoke_08", Color(0.6, 0.35, 0.8)],
	ElementChart.E.NEUTRAL:   ["star_07", Color(0.85, 0.88, 0.95)],
}

static func burst(parent: Node, at: Vector2, element: int, big: bool = false) -> void:
	var entry: Array = ELEMENT_FX.get(element, ELEMENT_FX[ElementChart.E.NEUTRAL])
	var path: String = P % entry[0]
	if not ResourceLoader.exists(path):
		path = P % "circle_05"
		if not ResourceLoader.exists(path): return
	var p := GPUParticles2D.new()
	p.texture = load(path)
	p.amount = 20 if big else 10
	p.lifetime = 0.7 if big else 0.45
	p.one_shot = true
	p.explosiveness = 0.92
	p.position = at
	p.z_index = 50
	var m := ParticleProcessMaterial.new()
	m.direction = Vector3(0, -1, 0)
	m.spread = 180.0
	m.initial_velocity_min = 50.0 if big else 30.0
	m.initial_velocity_max = 180.0 if big else 110.0
	m.gravity = Vector3(0, 220, 0)
	# The Kenney textures are large; at their own scale a single burst covers a
	# whole unit panel and hides the thing it is meant to punctuate.
	m.scale_min = 0.06 if big else 0.04
	m.scale_max = 0.20 if big else 0.13
	m.color = entry[1]
	p.process_material = m
	parent.add_child(p)
	p.emitting = true
	# self-clean once the burst has finished
	var t := parent.get_tree().create_timer(p.lifetime + 0.4)
	t.timeout.connect(func():
		if is_instance_valid(p): p.queue_free())

static func slash(parent: Node, at: Vector2) -> void:
	var path := P % "slash_01"
	if not ResourceLoader.exists(path): return
	var s := Sprite2D.new()
	s.texture = load(path)
	s.position = at
	s.z_index = 51
	s.rotation = randf_range(-0.6, 0.6)
	s.modulate = Color(1, 1, 1, 0.95)
	parent.add_child(s)
	s.scale = Vector2(0.35, 0.35)
	var tw := parent.create_tween()
	tw.tween_property(s, "scale", Vector2(0.6, 0.6), 0.22)
	tw.parallel().tween_property(s, "modulate:a", 0.0, 0.22)
	tw.tween_callback(func():
		if is_instance_valid(s): s.queue_free())
