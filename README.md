## Project Overview

This application focuses on mapping marine pollution in the Thermaikos Gulf, specifically in the area in front of Thessaloniki. It utilizes open data from the Port of Thessaloniki Authority (Ο.Λ.Θ.), based on measurements taken at the piers and outside the port basin near the central breakwater. Pollution is visualized through a heatmap, where higher pollution levels are displayed with more intense color. Two different formulas are used to calculate pollution scores based on measurements of physicochemical parameters. Each score falls into one of three categories: Low, Moderate, or High, as determined by bibliographic research. 

The added value of the application lies in the fact that it does not merely display raw data, but processes it to draw conclusions about pollution levels. Additionally, the user can select up to four measurement dates to compare results over time, displayed simultaneously on the map using a slider.

# Environmental Pollution Scoring Platform

This project runs on a minimal Ubuntu headless server and provides a web interface for visualizing marine pollution scores based on metal concentration thresholds. It combines three Docker containers: WordPress, MySQL, and a custom Java-based processing service.

---

## 1. Infrastructure Overview

The server hosts three primary containers:

- **WordPress**: Hosts the frontend interface using Elementor.
- **MySQL**: Stores WordPress content and settings.
- **Java Data Processor**: A custom Java container that processes Excel data and produces pollution scores in JSON format.

### URLs & Versions

- **Frontend URL**: `http://<server-ip>:8080`
- **Minimum Java Version for build**: Java 23

### Folder Structure on Server

```
/var/www/html/wp-content/uploads/         # Path inside the WordPress Docker container (mapped from the host)
/home/<user>/<anyfolder>/normalized/      # Excel input files and Dockerfile build context
/home/user/app/target/                    # Location of generated .jar and output JSON
```

MySQL is connected to WordPress via environment variables in the `docker-compose.yml`, defining host, username, password, and database. It is used exclusively for WordPress data persistence.

The Java container is built from a `Dockerfile` located in the main folder of the project on the server. This file defines how to build the container that will run the custom Java application. It typically:
- Uses an OpenJDK base image
- Copies the compiled `PollutionScoring.jar` into the container
- Sets the working directory
- Specifies an `ENTRYPOINT` to run the Java application automatically when the container starts

---

## 2. WordPress Setup & Elementor Integration

Plugins in use:

- **Elementor**: For designing pages and adding interactive map features.
- **Leaflet Maps Marker**: Used to display pollution data on a dynamic map interface.

**HTML Integration**: Use Elementor's “HTML” widget to insert the dynamic heatmap code. Paste the HTML and JavaScript code directly in the widget on the target page.

---

## 3. Java Processing Container

The application is executed inside a Docker container. It shares a volume with WordPress to export the `pollution_scores.json` directly to:

```
/var/www/html/wp-content/uploads/pollution_scores.json
```

This shared volume ensures the frontend can access the most recent scores without extra syncing or API calls.

---

## 4. Local Java Build Instructions

### GUI Method (NetBeans)

1. Clone our project from NetBeans.
2. Open the project if asked after check out or manually.
3. Right-click the project → *Clean and Build*.
4. The fat JAR will be created in `/target` (contains all dependencies used).

### Console Method

```bash
git clone https://github.com/parcharidism/DYPA_Sea_Pollution.git
cd DYPA_Sea_Pollution
mvn clean package
```

> Note: Building the fat JAR requires Maven and uses the provided `pom.xml` file in the repository. This configuration ensures the correct structure and dependencies for container execution.

This generates a standalone fat JAR at:
```
target/PollutionScoring.jar
```

---

## 5. Code Key Elements

### Input Files

- Place Excel `.xlsx` files inside:
  ```
  /home/<user>/<anyfolder>/normalized/
  ```

### Output File

- The JSON file is exported to:
  ```
  /var/www/html/wp-content/uploads/pollution_scores.json
  ```

This file is used by the frontend to display pollution levels on the interactive map.

---

## 6. Running the Scoring Service

The JAR file is executed inside a Java container built from the provided Dockerfile. The container mounts:

- `/home/<user>/<anyfolder>/normalized/` → for input Excel files
- `/var/www/html/wp-content/uploads/` → for output JSON

The JAR automatically processes all Excel files and generates the JSON output upon container startup.

---

## 7. Automated Execution: `run.sh` Script

To simplify the process of running the Java container, a shell script named `run.sh` is provided. This script runs the container, mounts the necessary directories, and triggers the scoring operation:

```bash
docker run --rm \
  -v wordpress_data:/app/output/wp-content/uploads \
  -v /home/<user>/<anyfolder>/normalized:/app/normalized \
  pollution-java /app/normalized
```

- The first volume (`wordpress_data`) allows the container to write the resulting JSON file directly to WordPress.
- The second volume points to the folder containing the Excel files.
- The container image (`pollution-java`) runs the Java JAR and processes the mounted Excel files from `/app/normalized`.

The script does not need to be placed in a specific folder to run, as long as the volume paths it references are correct.

Make sure to give the script execute permission before running it:

```bash
chmod +x run.sh
./run.sh
```
