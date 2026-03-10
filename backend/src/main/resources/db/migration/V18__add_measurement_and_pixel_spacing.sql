DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='image_info' AND column_name='pixel_spacing_x_mm') THEN
        ALTER TABLE image_info ADD COLUMN pixel_spacing_x_mm DECIMAL(10,5);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='image_info' AND column_name='pixel_spacing_y_mm') THEN
        ALTER TABLE image_info ADD COLUMN pixel_spacing_y_mm DECIMAL(10,5);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='image_annotation' AND column_name='measured_width_mm') THEN
        ALTER TABLE image_annotation ADD COLUMN measured_width_mm DECIMAL(10,3);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='image_annotation' AND column_name='measured_height_mm') THEN
        ALTER TABLE image_annotation ADD COLUMN measured_height_mm DECIMAL(10,3);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='image_annotation' AND column_name='compare_status') THEN
        ALTER TABLE image_annotation ADD COLUMN compare_status VARCHAR(20);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='image_annotation' AND column_name='compare_note') THEN
        ALTER TABLE image_annotation ADD COLUMN compare_note TEXT;
    END IF;
END $$;
