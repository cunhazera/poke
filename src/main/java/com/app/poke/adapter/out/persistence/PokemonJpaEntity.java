package com.app.poke.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The {@code pokemon} row and its child tables (schema owned by Flyway V1). Mutable on purpose, as JPA needs; it never
 * leaves this package: {@link JpaLocalPokemonGateway} maps it to and from the immutable domain model.
 */
@Entity
@Table(name = "pokemon")
class PokemonJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "pokedex_number", unique = true)
	private Integer pokedexNumber;

	// ---- catalog data (refreshed by every sync) ----
	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 100)
	private String category;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "height_dm", nullable = false)
	private int heightDm;

	@Column(name = "weight_hg", nullable = false)
	private int weightHg;

	@Column(name = "sprite_url", length = 500)
	private String spriteUrl;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Column(nullable = false)
	private short hp;

	@Column(nullable = false)
	private short attack;

	@Column(nullable = false)
	private short defense;

	@Column(name = "special_attack", nullable = false)
	private short specialAttack;

	@Column(name = "special_defense", nullable = false)
	private short specialDefense;

	@Column(nullable = false)
	private short speed;

	// The slot is an explicit column (not @OrderColumn, which Hibernate maps to INTEGER, but the schema says SMALLINT).
	@ElementCollection
	@CollectionTable(name = "pokemon_type", joinColumns = @JoinColumn(name = "pokemon_id"))
	@OrderBy("slot")
	private List<TypeEmbeddable> types = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "pokemon_ability", joinColumns = @JoinColumn(name = "pokemon_id"))
	@OrderBy("slot")
	private List<AbilityEmbeddable> abilities = new ArrayList<>();

	// ---- proprietary data (never touched by a sync) ----
	@ElementCollection
	@CollectionTable(name = "pokemon_localized_name", joinColumns = @JoinColumn(name = "pokemon_id"))
	@MapKeyColumn(name = "language", length = 10)
	@Column(name = "name", nullable = false, length = 100)
	private Map<String, String> localizedNames = new TreeMap<>();

	@Column(length = 50)
	private String region;

	@Column(length = 50)
	private String habitat;

	@Column(precision = 9, scale = 6)
	private BigDecimal latitude;

	@Column(precision = 9, scale = 6)
	private BigDecimal longitude;

	@ElementCollection
	@CollectionTable(name = "pokemon_tag", joinColumns = @JoinColumn(name = "pokemon_id"))
	@Column(name = "tag", nullable = false, length = 50)
	private Set<String> tags = new TreeSet<>();

	// ---- bookkeeping ----
	@Column(name = "synced_at")
	private Instant syncedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected PokemonJpaEntity() {
	}

	PokemonJpaEntity(Integer pokedexNumber) {
		this.pokedexNumber = pokedexNumber;
	}

	void setCatalogData(String name, String category, String description, int heightDm, int weightHg, String spriteUrl,
			String imageUrl, short[] stats, List<TypeEmbeddable> types, List<AbilityEmbeddable> abilities) {
		this.name = name;
		this.category = category;
		this.description = description;
		this.heightDm = heightDm;
		this.weightHg = weightHg;
		this.spriteUrl = spriteUrl;
		this.imageUrl = imageUrl;
		this.hp = stats[0];
		this.attack = stats[1];
		this.defense = stats[2];
		this.specialAttack = stats[3];
		this.specialDefense = stats[4];
		this.speed = stats[5];
		replace(this.types, types);
		replace(this.abilities, abilities);
	}

	void setProprietaryData(Map<String, String> localizedNames, String region, String habitat, BigDecimal latitude,
			BigDecimal longitude, Set<String> tags) {
		this.localizedNames.clear();
		this.localizedNames.putAll(localizedNames);
		this.region = region;
		this.habitat = habitat;
		this.latitude = latitude;
		this.longitude = longitude;
		this.tags.clear();
		this.tags.addAll(tags);
	}

	void setSyncedAt(Instant syncedAt) {
		this.syncedAt = syncedAt;
	}

	/** Mutates the managed collection in place, so Hibernate updates rows instead of replacing the collection. */
	private static <T> void replace(List<T> target, List<T> source) {
		target.clear();
		target.addAll(source);
	}

	Long id() {
		return id;
	}

	Integer pokedexNumber() {
		return pokedexNumber;
	}

	String name() {
		return name;
	}

	String category() {
		return category;
	}

	String description() {
		return description;
	}

	int heightDm() {
		return heightDm;
	}

	int weightHg() {
		return weightHg;
	}

	String spriteUrl() {
		return spriteUrl;
	}

	String imageUrl() {
		return imageUrl;
	}

	short[] stats() {
		return new short[] { hp, attack, defense, specialAttack, specialDefense, speed };
	}

	List<TypeEmbeddable> types() {
		return types;
	}

	List<AbilityEmbeddable> abilities() {
		return abilities;
	}

	Map<String, String> localizedNames() {
		return localizedNames;
	}

	String region() {
		return region;
	}

	String habitat() {
		return habitat;
	}

	BigDecimal latitude() {
		return latitude;
	}

	BigDecimal longitude() {
		return longitude;
	}

	Set<String> tags() {
		return tags;
	}

	Instant syncedAt() {
		return syncedAt;
	}

	long version() {
		return version;
	}
}
