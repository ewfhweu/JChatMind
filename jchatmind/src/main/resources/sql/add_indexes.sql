-- 添加parent_id的B-Tree索引
CREATE INDEX IF NOT EXISTS idx_chunk_bge_m3_parent_id ON chunk_bge_m3 USING btree (parent_id);

-- 添加doc_id的B-Tree索引
CREATE INDEX IF NOT EXISTS idx_chunk_bge_m3_doc_id ON chunk_bge_m3 USING btree (doc_id);
