-- Create databases that the microservice expects
CREATE DATABASE schema_sync_db;
CREATE DATABASE validation_db;
CREATE DATABASE test_db;
CREATE DATABASE prod_db;

-- Grant full privileges to sync_user on every database
GRANT ALL PRIVILEGES ON DATABASE schema_sync_db TO sync_user;
GRANT ALL PRIVILEGES ON DATABASE validation_db  TO sync_user;
GRANT ALL PRIVILEGES ON DATABASE test_db        TO sync_user;
GRANT ALL PRIVILEGES ON DATABASE prod_db        TO sync_user;
