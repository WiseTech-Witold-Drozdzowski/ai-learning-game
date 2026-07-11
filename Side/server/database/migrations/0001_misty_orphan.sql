ALTER TABLE `applications` ADD `contract_type` text;--> statement-breakpoint
ALTER TABLE `applications` ADD `remote_possible` integer DEFAULT false NOT NULL;--> statement-breakpoint
ALTER TABLE `applications` ADD `technologies` text DEFAULT '[]' NOT NULL;