// PIPELINE PROCESS

// Argh why am I so slow!! Let's just use Claude CODE!!!!

//--------------------------------------------------------
// 1) Ok first started with configurations
//--------------------------------------------------------
// FILE TO CUSTOMIZE: nextflow.config (for notes on what the parameters mean, see nextflow_schema.json)

// MY CONFIGURATION FOR star_rsem/kallisto (no salmon), only changed some of these in nextflow.config
// params {}
// References:
	skip_gtf_filter            = true
	skip_gtf_transcript_filter = true
	gencode                    = true // this affects running salmon only
	save_reference             = true
	igenomes_ignore            = true // (this is a hidden parameter, not shown in help message)

// UMI handling:
	umi_dedup_tool             = 'umicollapse'

// Trimming:
	extra_trimgalore_args      = '--quality 0 --illumina' // to avoid any quality trimming, just trim the Illumina universal adapter
    save_trimmed               = true

// BBSplit genome filtering
    skip_bbsplit               = true

// Alignment
    aligner                    = 'star_rsem'
    pseudo_aligner             = 'kallisto'
    star_ignore_sjdbgtf        = true // since I will use pre-built indexes

// QC
    skip_preseq                = false

// Boilerplate options
    show_hidden                = true

//--------------------------------------------------------
//--------------------------------------------------------

//--------------------------------------------------------
// 2) Then decided which ones to include in command line
//--------------------------------------------------------
// COMMAND LINE PARAMETERS
// Input options:
--input  [/path/to/samplesheet.csv] \
--outdir [/path/to/outdir] \
--email  [email_address] \
--multiqc_title [experiment_name] \

// References:
--fasta [/path/to/genome/fasta.gz] \
--gtf   [/path/to/annot/gtf.gz] \
--star_index [/path/to/directory] \
--rsem_index [/path/to/directory] \
--kallisto_index [/path/to/directory] \

// UMI handling:
--with_umi \          // if applicable, not all my libraries have UMIs
--skip_umi_extract \  // if --with_umi, then include this since the UMI is in index1 (not in-line in read1/2), so I don't think I need to do any extraction

// KALLISTO:
--extra_kallisto_quant_args ["--rf-stranded"] \ // specify extra arguments for kallisto, note that strandedness is taken from the sample sheet or calculated automatically, but can also set globally here
// For single-end data
--kallisto_quant_fraglen \    // required for single-end mode, default = 200
--kallisto_quant_fraglen_sd \ // same as above

//--------------------------------------------------------
//--------------------------------------------------------
// 3) Then checked what parameters are used for running STAR:

// These are from ENCODE long rna-seq (https://github.com/ENCODE-DCC/long-rna-seq-pipeline/blob/master/DAC/STAR_RSEM.sh)
STARparCommon=
--genomeDir $STARgenomeDir  
--readFilesIn $read1 $read2   
--outSAMunmapped Within 
--outFilterType BySJout \
 --outSAMattributes NH HI AS NM MD    
 --outFilterMultimapNmax 20   
 --outFilterMismatchNmax 999   \
 --outFilterMismatchNoverReadLmax 0.04   
 --alignIntronMin 20   
 --alignIntronMax 1000000   
 --alignMatesGapMax 1000000   \
 --alignSJoverhangMin 8   
 --alignSJDBoverhangMin 1 
 --sjdbScore 1 
 --readFilesCommand zcat

// These are the arguments I currently use:
STAR
ok--readFilesCommand zcat 
ok--readFilesIn /mnt/isilon/choi_lab/users/choip/data/nextseq2000/PC815/bcl_convert/${sample}_R1_001.fastq.gz /mnt/isilon/choi_lab/users/choip/data/nextseq2000/PC815/bcl_convert/${sample}_R2_001.fastq.gz 
ok--runThreadN 8 
ok--outFileNamePrefix /mnt/isilon/choi_lab/users/choip/analysis/pc815_celf3/star_align/${sample}. 
ok--quantMode TranscriptomeSAM GeneCounts 
ok--outSAMunmapped Within 
DIFFERENT--outSAMtype BAM SortedByCoordinate 
TO BE ADDED--twopassMode Basic 
ok--outFilterType BySJout 
ok--outFilterMultimapNmax 20 
ok--alignSJoverhangMin 8 
ok--alignSJDBoverhangMin 1 
ok--outFilterMismatchNmax 999 
ok--alignIntronMin 20 
ok--alignIntronMax 1000000 
ok--alignMatesGapMax 1000000

// This is where the nfcore arguments are found
    extra_star_align_args      = null // the ones for RSEM are found in /subworkflows/local/align_star/nextflow.config

       // Common args between pipeline and RSEM
    def base_args = """
        ok--quantMode TranscriptomeSAM
        --outSAMtype BAM Unsorted
        --outSAMattributes NH HI AS NM MD
        --readFilesCommand zcat
        (ADD --twopassMode Basic ?)
    """.trim()

    // Add quantifier-specific args
    if (quantifier == 'rsem') {
        // RSEM-compatible configuration
        base_args += " " + """
            ok--outSAMunmapped Within
            ok--outFilterType BySJout
            ok--outFilterMultimapNmax 20
            ok--outFilterMismatchNmax 999
            
            NOT IN MINE--outFilterMismatchNoverLmax 0.04
            	- This is not what's found in ENCODE!
            	- They are mixing this up with: --outFilterMismatchNoverReadLmax (actual parameter)
            
            ok--alignIntronMin 20
            ok--alignIntronMax 1000000
            ok--alignMatesGapMax 1000000
            ok--alignSJoverhangMin 8
            ok--alignSJDBoverhangMin 1

            NOT IN MINE--sjdbScore 1
            	- default: 2
				- int: extra alignment score for alignments that cross database junctions
        """.trim()
    } else {
        // Standard pipeline configuration
        base_args += " " + """
            --twopassMode Basic
            --runRNGseed 0
            --outFilterMultimapNmax 20
            --alignSJDBoverhangMin 1
            --outSAMstrandField intronMotif
        """.trim()
    }

/* OK, to modify the star arguments, here is what I ended up doing:
	edited: /subworkflows/local/align_star/nextflow.config
	to: def base_args = """
		added: --twopassMode Basic
	to: if (quantifier == 'rsem') {
		modified: --outFilterMismatchNoverLmax 0.04
		to:       --outFilterMismatchNoverReadLmax 0.04
	to: } else {
		removed: --twopassMode Basic (since I added it to the overall base_args above)
DONE */











Changes I want to make:

REFERENCES
done - the pipeline uses iGenomes but this is no longer recommended
	- nextflow.config --> changed ""

- bed file and chromsizes are needed by rseqc
	- can just let the pipeline generate them (see later whether it take a long time or not)

- Add different references? or can I just specify?

done - Add specific parameters to STAR
	done - Stick to twoPassmode BASIC (for now)
		- can consider twopassmode multi-sample as a future version
			Reason 1) probably not dramatically different
			Reason 2) needs to be tested
	done - ENCODE parameters


























- Add rMATS
	- Along with downstream filtering step

- Add MAJIQ 2.5
	- Along with downstream VOILA step

- Add MAJIQ 3.0
	- VOILA is weird for me?



EDITING MAIN.NF

- params are command-line configurable variables, so I can use this to add any new paramters and then define them either here or in the config files (nextflow.config)