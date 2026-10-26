package com.ecommerce.productordermanagement.repository;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecommerce.productordermanagement.entity.Product;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long> {
	List<Product> findAllByShopIdOrderByIdDesc(Long shopId);

	Optional<Product> findByIdAndShopId(Long id, Long shopId);

	long countByShopId(Long shopId);

	long countByShopIdAndStockLessThanEqual(Long shopId, int stock);

	List<Product> findAllByProductUrlIn(Collection<String> productUrls);

	List<Product> findAllBySourceIdentityIn(Collection<String> sourceIdentities);

	Optional<Product> findFirstByNameIgnoreCaseOrderByIdAsc(String name);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Product p where p.id = :id")
	Optional<Product> findByIdForUpdate(@Param("id") Long id);

	@Query("""
		select p from Product p
		where (p.imageUrl is not null and trim(p.imageUrl) != '')
		and (:search = '' or
		       (lower(coalesce(p.name, '')) like concat('%', :search, '%')
		        and function('regexp_like', lower(coalesce(p.name, '')), :searchPattern) = true) or
		       (lower(coalesce(p.description, '')) like concat('%', :search, '%')
		        and function('regexp_like', lower(coalesce(p.description, '')), :searchPattern) = true))
		and (lower(trim(:search)) not in ('saree', 'sarees', 'sari', 'saris')
		     or (lower(coalesce(p.name, '')) not like '%shapewear%'
		         and lower(coalesce(p.name, '')) not like '%petticoat%'))
		and (:category = '' or
		     lower(trim(coalesce(p.category, ''))) in :categoryValues or
		     lower(trim(coalesce(p.mainCategory, ''))) in :categoryValues or
		     lower(trim(coalesce(p.subCategory, ''))) in :categoryValues)
		and (:mainCategory = '' or lower(trim(coalesce(p.mainCategory, ''))) = lower(trim(:mainCategory)))
		and (:subCategory = '' or lower(trim(coalesce(p.subCategory, ''))) = lower(trim(:subCategory)))
		and (:gender = '' or
		     (:gender = 'women' and
		      (lower(coalesce(p.name, '')) like '%women%' or
		       lower(coalesce(p.mainCategory, '')) like '%women%' or
		       lower(coalesce(p.subCategory, '')) like '%women%' or
		       lower(coalesce(p.name, '')) like '%saree%' or
		       lower(coalesce(p.name, '')) like '%kurti%' or
		       lower(coalesce(p.name, '')) like '%dress%' or
		       lower(coalesce(p.name, '')) like '%jeans%' or
		       lower(coalesce(p.name, '')) like '%sandals%' or
		       lower(coalesce(p.name, '')) like '%handbag%' or
		       lower(coalesce(p.name, '')) like '%watch%')) or
		     (:gender = 'men' and
		      ((lower(coalesce(p.name, '')) like '%men%' and lower(coalesce(p.name, '')) not like '%women%') or
		       (lower(coalesce(p.mainCategory, '')) like '%men%' and lower(coalesce(p.mainCategory, '')) not like '%women%') or
		       (lower(coalesce(p.subCategory, '')) like '%men%' and lower(coalesce(p.subCategory, '')) not like '%women%') or
		       lower(coalesce(p.name, '')) like '%shirt%' or
		       lower(coalesce(p.name, '')) like '%trouser%' or
		       lower(coalesce(p.name, '')) like '%jeans%' or
		       lower(coalesce(p.name, '')) like '%wallet%' or
		       lower(coalesce(p.name, '')) like '%belt%' or
		       lower(coalesce(p.name, '')) like '%watch%')) or
		     (:gender = 'kids' and
		      (lower(coalesce(p.name, '')) like '%kids%' or
		       lower(coalesce(p.mainCategory, '')) like '%kids%' or
		       lower(coalesce(p.subCategory, '')) like '%kids%' or
		       lower(coalesce(p.mainCategory, '')) like '%boys%' or
		       lower(coalesce(p.mainCategory, '')) like '%girls%' or
		       lower(coalesce(p.name, '')) like '%kid%' or
		       lower(coalesce(p.name, '')) like '%boy%' or
		       lower(coalesce(p.name, '')) like '%girl%')))
		""")
	Page<Product> searchProductsByCategories(@Param("search") String search,
			@Param("searchPattern") String searchPattern,
			@Param("category") String category,
			@Param("categoryValues") Collection<String> categoryValues,
			@Param("mainCategory") String mainCategory,
			@Param("subCategory") String subCategory,
			@Param("gender") String gender,
			Pageable pageable);

	@Query("""
		select p from Product p
		where (p.imageUrl is not null and trim(p.imageUrl) != '')
		and ((lower(coalesce(p.name, '')) like concat('%', :search, '%')
		      and function('regexp_like', lower(coalesce(p.name, '')), :searchPattern) = true)
		     or (lower(coalesce(p.description, '')) like concat('%', :search, '%')
		         and function('regexp_like', lower(coalesce(p.description, '')), :searchPattern) = true))
		and (lower(trim(:search)) not in ('saree', 'sarees', 'sari', 'saris')
		     or (lower(coalesce(p.name, '')) not like '%shapewear%'
		         and lower(coalesce(p.name, '')) not like '%petticoat%'))
		and (:category = '' or
		     lower(trim(coalesce(p.category, ''))) in :categoryValues or
		     lower(trim(coalesce(p.mainCategory, ''))) in :categoryValues or
		     lower(trim(coalesce(p.subCategory, ''))) in :categoryValues)
		and (:mainCategory = '' or lower(trim(coalesce(p.mainCategory, ''))) = lower(trim(:mainCategory)))
		and (:subCategory = '' or lower(trim(coalesce(p.subCategory, ''))) = lower(trim(:subCategory)))
		order by case
			when lower(trim(coalesce(p.name, ''))) = lower(trim(:search)) then 0
			when lower(coalesce(p.name, '')) like concat('%', :search, '%')
			     and function('regexp_like', lower(coalesce(p.name, '')), :searchPattern) = true then 1
			else 2
		end, p.id
		""")
	Page<Product> searchProductsByRelevance(@Param("search") String search,
			@Param("searchPattern") String searchPattern,
			@Param("category") String category,
			@Param("categoryValues") Collection<String> categoryValues,
			@Param("mainCategory") String mainCategory,
			@Param("subCategory") String subCategory,
			Pageable pageable);

	@Query(value = """
		select p.* from products p
		where (p.image_url like 'http://%' or p.image_url like 'https://%')
		and match(p.name, p.description)
		      against (:searchTerms in boolean mode)
		and (:searchTerms not in ('+saree*', '+sarees*', '+sari*', '+saris*')
		     or (lower(coalesce(p.name, '')) not like '%shapewear%'
		         and lower(coalesce(p.name, '')) not like '%petticoat%'))
		and (:category = '' or
		     lower(trim(coalesce(p.category, ''))) in (:categoryValues) or
		     lower(trim(coalesce(p.main_category, ''))) in (:categoryValues) or
		     lower(trim(coalesce(p.sub_category, ''))) in (:categoryValues))
		and (:mainCategory = '' or lower(trim(coalesce(p.main_category, ''))) = lower(trim(:mainCategory)))
		and (:subCategory = '' or lower(trim(coalesce(p.sub_category, ''))) = lower(trim(:subCategory)))
		and (:gender = '' or
		     (:gender = 'women' and
		      (lower(coalesce(p.name, '')) like '%women%' or
		       lower(coalesce(p.main_category, '')) like '%women%' or
		       lower(coalesce(p.sub_category, '')) like '%women%' or
		       lower(coalesce(p.name, '')) like '%saree%' or
		       lower(coalesce(p.name, '')) like '%kurti%' or
		       lower(coalesce(p.name, '')) like '%dress%' or
		       lower(coalesce(p.name, '')) like '%jeans%' or
		       lower(coalesce(p.name, '')) like '%sandals%' or
		       lower(coalesce(p.name, '')) like '%handbag%' or
		       lower(coalesce(p.name, '')) like '%watch%')) or
		     (:gender = 'men' and
		      ((lower(coalesce(p.name, '')) like '%men%' and lower(coalesce(p.name, '')) not like '%women%') or
		       (lower(coalesce(p.main_category, '')) like '%men%' and lower(coalesce(p.main_category, '')) not like '%women%') or
		       (lower(coalesce(p.sub_category, '')) like '%men%' and lower(coalesce(p.sub_category, '')) not like '%women%') or
		       lower(coalesce(p.name, '')) like '%shirt%' or
		       lower(coalesce(p.name, '')) like '%trouser%' or
		       lower(coalesce(p.name, '')) like '%jeans%' or
		       lower(coalesce(p.name, '')) like '%wallet%' or
		       lower(coalesce(p.name, '')) like '%belt%' or
		       lower(coalesce(p.name, '')) like '%watch%')) or
		     (:gender = 'kids' and
		      (lower(coalesce(p.name, '')) like '%kids%' or
		       lower(coalesce(p.main_category, '')) like '%kids%' or
		       lower(coalesce(p.sub_category, '')) like '%kids%' or
		       lower(coalesce(p.main_category, '')) like '%boys%' or
		       lower(coalesce(p.main_category, '')) like '%girls%' or
		       lower(coalesce(p.name, '')) like '%kid%' or
		       lower(coalesce(p.name, '')) like '%boy%' or
		       lower(coalesce(p.name, '')) like '%girl%')))
		""",
		countQuery = """
		select count(*) from products p
		where (p.image_url like 'http://%' or p.image_url like 'https://%')
		and match(p.name, p.description)
		      against (:searchTerms in boolean mode)
		and (:searchTerms not in ('+saree*', '+sarees*', '+sari*', '+saris*')
		     or (lower(coalesce(p.name, '')) not like '%shapewear%'
		         and lower(coalesce(p.name, '')) not like '%petticoat%'))
		and (:category = '' or
		     lower(trim(coalesce(p.category, ''))) in (:categoryValues) or
		     lower(trim(coalesce(p.main_category, ''))) in (:categoryValues) or
		     lower(trim(coalesce(p.sub_category, ''))) in (:categoryValues))
		and (:mainCategory = '' or lower(trim(coalesce(p.main_category, ''))) = lower(trim(:mainCategory)))
		and (:subCategory = '' or lower(trim(coalesce(p.sub_category, ''))) = lower(trim(:subCategory)))
		and (:gender = '' or
		     (:gender = 'women' and
		      (lower(coalesce(p.name, '')) like '%women%' or
		       lower(coalesce(p.main_category, '')) like '%women%' or
		       lower(coalesce(p.sub_category, '')) like '%women%' or
		       lower(coalesce(p.name, '')) like '%saree%' or
		       lower(coalesce(p.name, '')) like '%kurti%' or
		       lower(coalesce(p.name, '')) like '%dress%' or
		       lower(coalesce(p.name, '')) like '%jeans%' or
		       lower(coalesce(p.name, '')) like '%sandals%' or
		       lower(coalesce(p.name, '')) like '%handbag%' or
		       lower(coalesce(p.name, '')) like '%watch%')) or
		     (:gender = 'men' and
		      ((lower(coalesce(p.name, '')) like '%men%' and lower(coalesce(p.name, '')) not like '%women%') or
		       (lower(coalesce(p.main_category, '')) like '%men%' and lower(coalesce(p.main_category, '')) not like '%women%') or
		       (lower(coalesce(p.sub_category, '')) like '%men%' and lower(coalesce(p.sub_category, '')) not like '%women%') or
		       lower(coalesce(p.name, '')) like '%shirt%' or
		       lower(coalesce(p.name, '')) like '%trouser%' or
		       lower(coalesce(p.name, '')) like '%jeans%' or
		       lower(coalesce(p.name, '')) like '%wallet%' or
		       lower(coalesce(p.name, '')) like '%belt%' or
		       lower(coalesce(p.name, '')) like '%watch%')) or
		     (:gender = 'kids' and
		      (lower(coalesce(p.name, '')) like '%kids%' or
		       lower(coalesce(p.main_category, '')) like '%kids%' or
		       lower(coalesce(p.sub_category, '')) like '%kids%' or
		       lower(coalesce(p.main_category, '')) like '%boys%' or
		       lower(coalesce(p.main_category, '')) like '%girls%' or
		       lower(coalesce(p.name, '')) like '%kid%' or
		       lower(coalesce(p.name, '')) like '%boy%' or
		       lower(coalesce(p.name, '')) like '%girl%')))
		""",
		nativeQuery = true)
	Page<Product> searchProductsFullText(@Param("searchTerms") String searchTerms,
			@Param("category") String category,
			@Param("categoryValues") Collection<String> categoryValues,
			@Param("mainCategory") String mainCategory,
			@Param("subCategory") String subCategory,
			@Param("gender") String gender,
			Pageable pageable);

	@Query(value = """
		select count(*) > 0 from information_schema.statistics
		where table_schema = database()
		and table_name = 'products'
		and index_name = 'idx_products_text_search'
		and index_type = 'FULLTEXT'
		""", nativeQuery = true)
	boolean hasCatalogFullTextIndex();

	@Query(value = """
		select
			(select count(*) from "INFORMATION_SCHEMA"."TABLES"
			 where upper("TABLE_SCHEMA") = 'FT' and upper("TABLE_NAME") = 'INDEXES') > 0
			and
			(select count(*) from "INFORMATION_SCHEMA"."ROUTINES"
			 where upper("ROUTINE_SCHEMA") = 'PUBLIC' and upper("ROUTINE_NAME") = 'FT_SEARCH_DATA') > 0
			and
			(select count(*) from "INFORMATION_SCHEMA"."COLUMNS"
			 where upper("TABLE_SCHEMA") = 'PUBLIC'
			   and upper("TABLE_NAME") = 'PRODUCTS'
			   and upper("COLUMN_NAME") in ('CATEGORY_SEARCH_KEY',
			       'MAIN_CATEGORY_SEARCH_KEY', 'SUB_CATEGORY_SEARCH_KEY')) = 3
		""", nativeQuery = true)
	boolean hasH2CatalogSearchSupport();

	@Query(value = """
		select count(*) = 3 from "INFORMATION_SCHEMA"."COLUMNS"
		where upper("TABLE_SCHEMA") = 'PUBLIC'
		  and upper("TABLE_NAME") = 'PRODUCTS'
		  and upper("COLUMN_NAME") in ('CATEGORY_SEARCH_KEY',
		      'MAIN_CATEGORY_SEARCH_KEY', 'SUB_CATEGORY_SEARCH_KEY')
		""", nativeQuery = true)
	boolean hasH2CategorySearchKeys();

	@Query(value = """
		select count(*) > 0 from "FT"."INDEXES"
		where upper("SCHEMA") = 'PUBLIC'
		  and upper("TABLE") = 'PRODUCTS'
		  and upper("COLUMNS") = 'NAME, DESCRIPTION'
		""", nativeQuery = true)
	boolean hasH2ProductTextIndex();

	@Query(value = """
		select p.* from products p
		join "PUBLIC"."FT_SEARCH_DATA"(:search, 0, 0) ft on p.id = ft."KEYS"[1]
		where (p.image_url like 'http://%' or p.image_url like 'https://%')
		and ((lower(coalesce(p.name, '')) like concat('%', :search, '%')
		      and regexp_like(lower(coalesce(p.name, '')), :searchPattern))
		     or (lower(coalesce(p.description, '')) like concat('%', :search, '%')
		         and regexp_like(lower(coalesce(p.description, '')), :searchPattern)))
		and (:search not in ('saree', 'sarees', 'sari', 'saris')
		     or (lower(coalesce(p.name, '')) not like '%shapewear%'
		         and lower(coalesce(p.name, '')) not like '%petticoat%'))
		and (:category = '' or p.category_search_key in (:categoryValues)
		     or p.main_category_search_key in (:categoryValues)
		     or p.sub_category_search_key in (:categoryValues))
		and (:mainCategory = '' or p.main_category_search_key = :mainCategory)
		and (:subCategory = '' or p.sub_category_search_key = :subCategory)
		order by case
			when :prioritizeName = true
			     and lower(trim(coalesce(p.name, ''))) = :search then 0
			when :prioritizeName = true
			     and lower(coalesce(p.name, '')) like concat('%', :search, '%')
			     and regexp_like(lower(coalesce(p.name, '')), :searchPattern) then 1
			else 2
		end,
		case when :sortBy = 'price' and :direction = 'asc' then p.price end asc,
		case when :sortBy = 'price' and :direction = 'desc' then p.price end desc,
		case when :sortBy = 'name' and :direction = 'asc' then p.name end asc,
		case when :sortBy = 'name' and :direction = 'desc' then p.name end desc,
		case when :direction = 'desc' then p.id end desc,
		case when :direction = 'asc' then p.id end asc
		""",
		countQuery = """
		select count(*) from products p
		join "PUBLIC"."FT_SEARCH_DATA"(:search, 0, 0) ft on p.id = ft."KEYS"[1]
		where (p.image_url like 'http://%' or p.image_url like 'https://%')
		and ((lower(coalesce(p.name, '')) like concat('%', :search, '%')
		      and regexp_like(lower(coalesce(p.name, '')), :searchPattern))
		     or (lower(coalesce(p.description, '')) like concat('%', :search, '%')
		         and regexp_like(lower(coalesce(p.description, '')), :searchPattern)))
		and (:search not in ('saree', 'sarees', 'sari', 'saris')
		     or (lower(coalesce(p.name, '')) not like '%shapewear%'
		         and lower(coalesce(p.name, '')) not like '%petticoat%'))
		and (:category = '' or p.category_search_key in (:categoryValues)
		     or p.main_category_search_key in (:categoryValues)
		     or p.sub_category_search_key in (:categoryValues))
		and (:mainCategory = '' or p.main_category_search_key = :mainCategory)
		and (:subCategory = '' or p.sub_category_search_key = :subCategory)
		""", nativeQuery = true)
	Page<Product> searchProductsH2FullText(
			@Param("search") String search,
			@Param("searchPattern") String searchPattern,
			@Param("category") String category,
			@Param("categoryValues") Collection<String> categoryValues,
			@Param("mainCategory") String mainCategory,
			@Param("subCategory") String subCategory,
			@Param("prioritizeName") boolean prioritizeName,
			@Param("sortBy") String sortBy,
			@Param("direction") String direction,
			Pageable pageable);

	@Query(value = """
		select p.* from products p
		join (
			select id from products
			where category_search_key in (:categoryValues)
			  and (:mainCategory = '' or main_category_search_key = :mainCategory)
			  and (:subCategory = '' or sub_category_search_key = :subCategory)
			union
			select id from products
			where main_category_search_key in (:categoryValues)
			  and (:mainCategory = '' or main_category_search_key = :mainCategory)
			  and (:subCategory = '' or sub_category_search_key = :subCategory)
			union
			select id from products
			where sub_category_search_key in (:categoryValues)
			  and (:mainCategory = '' or main_category_search_key = :mainCategory)
			  and (:subCategory = '' or sub_category_search_key = :subCategory)
		) matched on matched.id = p.id
		where (p.image_url like 'http://%' or p.image_url like 'https://%')
		""",
		countQuery = """
		select count(*) from (
			select id from products
			where category_search_key in (:categoryValues)
			  and (:mainCategory = '' or main_category_search_key = :mainCategory)
			  and (:subCategory = '' or sub_category_search_key = :subCategory)
			union
			select id from products
			where main_category_search_key in (:categoryValues)
			  and (:mainCategory = '' or main_category_search_key = :mainCategory)
			  and (:subCategory = '' or sub_category_search_key = :subCategory)
			union
			select id from products
			where sub_category_search_key in (:categoryValues)
			  and (:mainCategory = '' or main_category_search_key = :mainCategory)
			  and (:subCategory = '' or sub_category_search_key = :subCategory)
		) matched
		""", nativeQuery = true)
	Page<Product> filterProductsH2ByCategory(
			@Param("categoryValues") Collection<String> categoryValues,
			@Param("mainCategory") String mainCategory,
			@Param("subCategory") String subCategory,
			Pageable pageable);

	@Query(value = """
		select p.* from products p
		where p.main_category_search_key = :mainCategory
		and (:subCategory = '' or p.sub_category_search_key = :subCategory)
		and (p.image_url like 'http://%' or p.image_url like 'https://%')
		""",
		countQuery = """
		select count(*) from products p
		where p.main_category_search_key = :mainCategory
		and (:subCategory = '' or p.sub_category_search_key = :subCategory)
		""", nativeQuery = true)
	Page<Product> filterProductsH2ByMainCategory(
			@Param("mainCategory") String mainCategory,
			@Param("subCategory") String subCategory,
			Pageable pageable);

	@Query(value = """
		select p.* from products p
		where p.sub_category_search_key = :subCategory
		and (p.image_url like 'http://%' or p.image_url like 'https://%')
		""",
		countQuery = """
		select count(*) from products p
		where p.sub_category_search_key = :subCategory
		""", nativeQuery = true)
	Page<Product> filterProductsH2BySubCategory(
			@Param("subCategory") String subCategory,
			Pageable pageable);

	@Query(value = """
		select p.* from products p
		where (p.image_url like 'http://%' or p.image_url like 'https://%')
		and (lower(trim(coalesce(p.category, ''))) in (:categoryValues)
		     or lower(trim(coalesce(p.main_category, ''))) in (:categoryValues)
		     or lower(trim(coalesce(p.sub_category, ''))) in (:categoryValues))
		""",
		countQuery = """
		select count(*) from products p
		where (p.image_url like 'http://%' or p.image_url like 'https://%')
		and (lower(trim(coalesce(p.category, ''))) in (:categoryValues)
		     or lower(trim(coalesce(p.main_category, ''))) in (:categoryValues)
		     or lower(trim(coalesce(p.sub_category, ''))) in (:categoryValues))
		""",
		nativeQuery = true)
	Page<Product> findByCatalogCategoryMySql(
			@Param("categoryValues") Collection<String> categoryValues,
			Pageable pageable);

	default Page<Product> searchProductsFullText(
			String searchTerms, String category, String mainCategory, String subCategory,
			String gender, Pageable pageable) {
		String normalizedCategory = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
		String normalizedGender = gender == null ? "" : gender.trim().toLowerCase(Locale.ROOT);
		return searchProductsFullText(searchTerms, normalizedCategory,
				categoryValues(normalizedCategory),
				mainCategory == null ? "" : mainCategory.trim(),
				subCategory == null ? "" : subCategory.trim(),
				normalizedGender, pageable);
	}

	@Query("""
		select p from Product p
		where (p.imageUrl is not null and trim(p.imageUrl) != '')
		""")
	Page<Product> findAllWithImage(Pageable pageable);

	@Query("""
		select p from Product p
		where (p.imageUrl is not null and trim(p.imageUrl) != '')
		""")
	List<Product> findFeaturedWithImage(Pageable pageable);

	default Page<Product> searchProducts(String search, String category, String gender, Pageable pageable) {
		String normalizedSearch = search == null ? "" : search.trim();
		return searchProducts(normalizedSearch, category, "", "", gender, pageable);
	}

	default Page<Product> searchProducts(String search, String category, String mainCategory,
			String subCategory, String gender, Pageable pageable) {
		String normalizedCategory = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
		String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		String normalizedGender = gender == null ? "" : gender.trim().toLowerCase(Locale.ROOT);
		String searchPattern = "(?<![\\p{L}\\p{N}])"
				+ java.util.regex.Pattern.quote(normalizedSearch) + "(?![\\p{L}\\p{N}])";
		return searchProductsByCategories(normalizedSearch, searchPattern, normalizedCategory,
				categoryValues(normalizedCategory),
				mainCategory == null ? "" : mainCategory.trim(),
				subCategory == null ? "" : subCategory.trim(),
				normalizedGender, pageable);
	}

	default Page<Product> searchProductsByRelevance(String search, String category,
			String mainCategory, String subCategory, Pageable pageable) {
		String normalizedCategory = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
		String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		String searchPattern = "(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(normalizedSearch)
				+ "(?![\\p{L}\\p{N}])";
		return searchProductsByRelevance(normalizedSearch, searchPattern, normalizedCategory,
				categoryValues(normalizedCategory),
				mainCategory == null ? "" : mainCategory.trim(),
				subCategory == null ? "" : subCategory.trim(), pageable);
	}

	default List<String> categoryValues(String category) {
		return switch (category) {
			case "electronics" -> List.of("electronics", "all electronics", "tv, audio & cameras",
					"cameras", "car electronics", "gaming consoles", "headphones",
					"home audio & theater", "security cameras", "speakers", "televisions");
			case "accessories" -> List.of("accessories", "car accessories", "camera accessories",
					"fitness accessories", "gaming accessories", "motorbike accessories & parts",
					"travel accessories");
			default -> List.of(category);
		};
	}

	@Query(value = """
		select trim(category) as catalog_category from products where category is not null and trim(category) <> ''
		union
		select trim(main_category) as catalog_category from products where main_category is not null and trim(main_category) <> ''
		union
		select trim(sub_category) as catalog_category from products where sub_category is not null and trim(sub_category) <> ''
		order by catalog_category
		""", nativeQuery = true)
	List<String> findDistinctCategories();

	@Query(value = """
		select distinct trim(sub_category) as catalog_sub_category
		from products
		where sub_category is not null and trim(sub_category) <> ''
		order by catalog_sub_category
		""", nativeQuery = true)
	List<String> findDistinctSubCategories();

	long countByStockLessThanEqual(int threshold);

}