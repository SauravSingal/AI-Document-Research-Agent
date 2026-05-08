 docker run -d   --name pgvector-db   -e POSTGRES_DB=aiagent_db   -e POSTGRES_USER=postgres   -e POSTGRES_PASSWORD=postgres   -p 5432:5432   ankane/pgvector
to start postgres pgvector in docker
