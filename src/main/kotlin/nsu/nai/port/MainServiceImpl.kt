package nsu.nai.port

import io.grpc.stub.StreamObserver
import nai.MainServiceGrpc
import nai.Nai

class MainServiceImpl : MainServiceGrpc.MainServiceImplBase() {
    override fun createGallery(
        request: Nai.CreateGalleryRequest?,
        responseObserver: StreamObserver<Nai.CreateGalleryResponse?>?
    ) {
        super.createGallery(request, responseObserver)
    }

    override fun getGalleries(
        request: Nai.GetGalleriesRequest?,
        responseObserver: StreamObserver<Nai.GetGalleriesResponse?>?
    ) {
        super.getGalleries(request, responseObserver)
    }

    override fun deleteGallery(
        request: Nai.DeleteGalleryRequest?,
        responseObserver: StreamObserver<Nai.DeleteGalleryResponse?>?
    ) {
        super.deleteGallery(request, responseObserver)
    }
}