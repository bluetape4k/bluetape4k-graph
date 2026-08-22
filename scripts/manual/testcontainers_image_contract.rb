require "optparse"

module TestcontainersImageContract
  EXPECTED_IMAGES = {
    "neo4j" => { image: "neo4j", tag: "5.26.29", reference: "neo4j:5.26.29" },
    "memgraph" => { image: "memgraph/memgraph", tag: "3.12.0", reference: "memgraph/memgraph:3.12.0" },
    "age" => { image: "apache/age", tag: "release_PG18_1.7.0", reference: "apache/age:release_PG18_1.7.0" },
    "falkordb" => { image: "falkordb/falkordb", tag: "v4.20.2", reference: "falkordb/falkordb:v4.20.2" },
  }.freeze

  EXPECTED_FAMILIES = EXPECTED_IMAGES.transform_values { |value| value.fetch(:image) }.freeze

  EXPECTED_CATALOG_VERSIONS = {
    "bluetape4k-bom" => "2.0.0-SNAPSHOT",
    "kotlin" => "2.4.10",
    "jfalkordb" => "0.8.0",
    "neo4j-driver6" => "6.2.1",
    "postgresql" => "42.7.13",
  }.freeze

  EXPECTED_DOCUMENT_TOKENS = [
    "Java 25",
    "Kotlin 2.4.10",
    "Neo4j Java Driver 6.2.1",
    "PostgreSQL JDBC 42.7.13",
    "jfalkordb 0.8.0",
    "2.0.0-SNAPSHOT",
  ].freeze

  FLOATING_OR_STALE_REFERENCES = {
    "neo4j" => ["`neo4j:5`", "`neo4j:5.26.24`"],
    "memgraph" => ["`memgraph/memgraph`", "`memgraph/memgraph:3.9.0`", "`memgraph/memgraph:latest`"],
    "age" => ["`apache/age:PG16_latest`"],
    "falkordb" => ["`falkordb/falkordb:v4.18.1`"],
  }.freeze

  BACKEND_DOCUMENT_CONTRACTS = {
    "graph/graph-neo4j/README.md" => {
      required: ["Neo4j Java Driver 6.2.1"],
      stale: ["Neo4j Java Driver 5.x"],
    },
    "graph/graph-neo4j/README.ko.md" => {
      required: ["Neo4j Java Driver 6.2.1"],
      stale: ["Neo4j Java Driver 5.x"],
    },
    "graph/graph-memgraph/README.md" => {
      required: ["memgraph/memgraph:3.12.0"],
      stale: ["memgraph/memgraph:latest"],
    },
    "graph/graph-memgraph/README.ko.md" => {
      required: ["memgraph/memgraph:3.12.0"],
      stale: ["memgraph/memgraph:latest"],
    },
    "graph/graph-age/README.md" => {
      required: ["apache/age:release_PG18_1.7.0"],
      stale: ["apache/age:PG16_latest"],
    },
    "graph/graph-age/README.ko.md" => {
      required: ["apache/age:release_PG18_1.7.0"],
      stale: ["apache/age:PG16_latest"],
    },
    "graph/graph-falkordb/README.md" => {
      required: ["jfalkordb", "0.8.0", "falkordb/falkordb:v4.20.2"],
      stale: ["0.7.0", "falkordb/falkordb:v4.18.1"],
    },
    "graph/graph-falkordb/README.ko.md" => {
      required: ["jfalkordb", "0.8.0", "falkordb/falkordb:v4.20.2"],
      stale: ["0.7.0", "falkordb/falkordb:v4.18.1"],
    },
  }.freeze

  BACKEND_KDOC_CONTRACTS = {
    "graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSchemaManager.kt" => {
      required: ["jfalkordb 0.8.0"],
      stale: ["jfalkordb 0.7.0"],
    },
    "graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSuspendOperations.kt" => {
      required: ["jfalkordb 0.8.0"],
      stale: ["jfalkordb 0.7.0"],
    },
  }.freeze

  class Validator
    attr_reader :errors

    def initialize(repository_root:, catalog_path:)
      @repository_root = File.expand_path(repository_root)
      @catalog_path = File.expand_path(catalog_path)
      @errors = validate.sort
    end

    private

    def validate
      errors = []
      manifest = read_file(File.join(@repository_root, ".github/testcontainers-images.txt"), errors, "image manifest")
      families = read_file(File.join(@repository_root, ".github/testcontainers-image-families.txt"), errors, "image family map")
      errors.concat(validate_images(manifest)) if manifest
      errors.concat(validate_families(families)) if families

      catalog = read_file(@catalog_path, errors, "central catalog")
      errors.concat(validate_catalog(catalog)) if catalog

      errors.concat(validate_gradle_baseline)
      errors.concat(validate_catalog_reference)
      errors.concat(validate_readmes)
      errors.concat(validate_backend_documents)
      errors
    end

    def read_file(path, errors, label)
      return File.read(path) if File.file?(path)

      errors << "#{label} not found: #{path}"
      nil
    rescue SystemCallError => error
      errors << "#{label} cannot be read: #{error.message}"
      nil
    end

    def validate_images(contents)
      images = contents.lines.map(&:strip).reject { |line| line.empty? || line.start_with?("#") }
      expected = EXPECTED_IMAGES.values.map { |value| value.fetch(:reference) }
      errors = []
      errors << "image manifest must contain exactly #{expected.join(', ')}" unless images == expected
      errors << "image manifest contains duplicate entries" unless images.uniq.length == images.length
      errors
    end

    def validate_families(contents)
      actual = contents.lines.each_with_object({}) do |line, result|
        value = line.sub(/#.*/, "").strip
        next if value.empty?

        family, image = value.split("=", 2).map(&:strip)
        result[family] = image
      end
      errors = []
      errors << "image family map must match #{EXPECTED_FAMILIES.inspect}" unless actual == EXPECTED_FAMILIES
      errors
    end

    def validate_catalog(contents)
      versions = contents.lines.each_with_object({}) do |line, result|
        match = line.match(/^\s*([A-Za-z0-9_-]+)\s*=\s*"([^"]+)"/)
        result[match[1]] = match[2] if match
      end
      EXPECTED_CATALOG_VERSIONS.each_with_object([]) do |(key, expected), errors|
        actual = versions[key]
        errors << "central catalog #{key} must be #{expected}, got #{actual.inspect}" unless actual == expected
      end
    end

    def validate_gradle_baseline
      contents = read_file(File.join(@repository_root, "build.gradle.kts"), [], "build baseline")
      return ["build baseline not found"] unless contents

      checks = {
        "JavaLanguageVersion.of(25)" => /JavaLanguageVersion\.of\(25\)/,
        "jvmToolchain(25)" => /jvmToolchain\(25\)/,
        "KotlinVersion.KOTLIN_2_4" => /KotlinVersion\.KOTLIN_2_4/,
      }
      checks.each_with_object([]) do |(label, pattern), errors|
        errors << "build baseline is missing #{label}" unless contents.match?(pattern)
      end
    end

    def validate_catalog_reference
      settings = read_file(File.join(@repository_root, "settings.gradle.kts"), [], "settings catalog reference")
      ci = read_file(File.join(@repository_root, ".github/workflows/ci.yml"), [], "CI catalog reference")
      return ["catalog reference sources are missing"] unless settings && ci

      settings_ref = settings[/\.orElse\("([0-9a-f]{40,64})"\)/, 1]
      ci_ref = ci[/BLUETAPE4K_DEPENDENCIES_CATALOG_REF:\s*['"]([0-9a-f]{40,64})['"]/, 1]
      errors = []
      errors << "settings catalog reference is missing or invalid" unless settings_ref
      errors << "CI catalog reference is missing or invalid" unless ci_ref
      errors << "settings and CI catalog references differ" if settings_ref && ci_ref && settings_ref != ci_ref
      errors
    end

    def validate_readmes
      documents = %w[README.md README.ko.md].map do |name|
        [name, read_file(File.join(@repository_root, name), [], name)]
      end
      errors = []
      documents.each do |name, contents|
        unless contents
          errors << "#{name} not found"
          next
        end

        EXPECTED_IMAGES.each_value do |image|
          reference = image.fetch(:reference)
          errors << "#{name} is missing #{reference}" unless contents.include?("`#{reference}`")
        end
        EXPECTED_DOCUMENT_TOKENS.each do |token|
          errors << "#{name} is missing #{token}" unless contents.include?(token)
        end
        FLOATING_OR_STALE_REFERENCES.each_value do |references|
          references.each do |reference|
            errors << "#{name} contains stale or floating #{reference}" if contents.include?(reference)
          end
        end
      end

      if documents.all? { |_, contents| contents }
        en_tokens = contract_tokens(documents.fetch(0).last)
        ko_tokens = contract_tokens(documents.fetch(1).last)
        errors << "English and Korean README contract tokens differ" unless en_tokens == ko_tokens
      end
      errors
    end

    def validate_backend_documents
      BACKEND_DOCUMENT_CONTRACTS.merge(BACKEND_KDOC_CONTRACTS).each_with_object([]) do |(relative_path, contract), errors|
        contents = read_file(File.join(@repository_root, relative_path), errors, relative_path)
        next unless contents

        contract.fetch(:required).each do |token|
          errors << "#{relative_path} is missing #{token}" unless contents.include?(token)
        end
        contract.fetch(:stale).each do |token|
          errors << "#{relative_path} contains stale #{token}" if contents.include?(token)
        end
      end
    end

    def contract_tokens(contents)
      EXPECTED_IMAGES.values.map { |value| value.fetch(:reference) }.select { |token| contents.include?(token) } +
        EXPECTED_DOCUMENT_TOKENS.select { |token| contents.include?(token) }
    end
  end
end

if $PROGRAM_NAME == __FILE__
  options = {
    repository_root: Dir.pwd,
    catalog_path: ENV["BLUETAPE4K_CATALOG_PATH"],
  }
  OptionParser.new do |parser|
    parser.banner = "Usage: ruby scripts/manual/testcontainers_image_contract.rb [options]"
    parser.on("--repository-root PATH") { |value| options[:repository_root] = value }
    parser.on("--catalog PATH") { |value| options[:catalog_path] = value }
  end.parse!

  abort("--catalog PATH is required") unless options[:catalog_path]

  validator = TestcontainersImageContract::Validator.new(**options)
  if validator.errors.empty?
    puts "Testcontainers image contract valid: 4 backend families, README locales, JDK/Kotlin baseline, and central catalog aligned; changed families remain gated by #1337."
  else
    warn validator.errors.join("\n")
    exit 1
  end
end
