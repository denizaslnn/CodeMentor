-- DROP analysis_tasks table as it is duplicate and not used by any production flow.
-- The system uses analysis_requests table.
DROP TABLE IF EXISTS analysis_tasks;
