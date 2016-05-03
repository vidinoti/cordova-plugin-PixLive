module.exports = function(grunt) {

    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),

        clean: ["build"],

        jsdoc: {
            dist: {
              src: ['www'],
              options: {
                destination: 'build/docs',
                readme: './README.md'
              }
            }
        }

        
    });

    grunt.loadNpmTasks('grunt-contrib-clean');
    grunt.loadNpmTasks('grunt-jsdoc');

    //Where we tell Grunt what to do when we type "grunt" into the terminal.
    grunt.registerTask('default', [
        'clean', 'jsdoc'
    ]);
};