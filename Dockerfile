FROM eclipse-temurin:21-jdk

# Install Clojure CLI
RUN apt-get update && apt-get install -y curl rlwrap && \
    curl -L https://download.clojure.org/install/linux-install.sh | bash && \
    rm -rf /var/lib/apt/lists/*

# Install Node.js 22
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install npm dependencies first (cache layer)
COPY package.json package-lock.json* ./
RUN mkdir -p static/css && npm install

# Copy deps.edn and download Clojure dependencies (cache layer)
COPY deps.edn ./
RUN clj -P

# Copy the rest of the project
COPY . .

# Build CSS
RUN npm run build

COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 5001

ENTRYPOINT ["/docker-entrypoint.sh"]
