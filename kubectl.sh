export version=1.08
echo $version

export region=us-central1
echo $region

export project_id=ust-gcp-costco-replatform-poc
echo $project_id

export ar_repo_nm=data-track-foundation
echo $ar_repo_nm

export image_nm=integration-framework-be
echo $image_nm

docker images
docker build -t $image_nm:$version .
docker tag $image_nm:$version $region-docker.pkg.dev/$project_id/$ar_repo_nm/$image_nm:$version
docker rmi $image_nm:$version
docker push $region-docker.pkg.dev/$project_id/$ar_repo_nm/$image_nm:$version
docker rmi $region-docker.pkg.dev/$project_id/$ar_repo_nm/$image_nm:$version
docker images
