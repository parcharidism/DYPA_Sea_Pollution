docker run --rm \
  -v wordpress_data:/app/output/wp-content/uploads \
  -v /home/parcha/pollution/normalized:/app/normalized \
  pollution-java /app/normalized