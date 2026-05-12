#!/usr/bin/env bash
set -euo pipefail

mode="${1:-}"
cache_dir="${DOCKER_IMAGE_CACHE_DIR:-/tmp/testcontainers-image-cache}"
images_file="${TESTCONTAINERS_IMAGES_FILE:-.github/testcontainers-images.txt}"

if [[ "$mode" != "load" && "$mode" != "save" ]]; then
  echo "Usage: $0 <load|save>" >&2
  exit 2
fi

mkdir -p "$cache_dir"

archive_name() {
  local image="$1"
  printf '%s' "$image" | sed -e 's#[/:@]#_#g'
}

read_images() {
  sed -e 's/#.*$//' -e '/^[[:space:]]*$/d' "$images_file"
}

if [[ "$mode" == "load" ]]; then
  shopt -s nullglob
  archives=("$cache_dir"/*.tar)
  if (( ${#archives[@]} == 0 )); then
    echo "No cached Docker image archives found in $cache_dir"
    exit 0
  fi

  for archive in "${archives[@]}"; do
    echo "Loading cached Docker image archive: $archive"
    docker load -i "$archive"
  done
  exit 0
fi

while IFS= read -r image; do
  archive="$cache_dir/$(archive_name "$image").tar"
  if docker image inspect "$image" >/dev/null 2>&1; then
    echo "Saving Docker image to cache: $image -> $archive"
    docker save -o "$archive" "$image"
  else
    echo "Image was not pulled by this job, skipping cache save: $image"
  fi
done < <(read_images)
